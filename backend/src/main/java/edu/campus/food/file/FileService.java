package edu.campus.food.file;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {
    private static final Set<String> PURPOSES = Set.of("SUPPLIER_CERT", "BATCH_VOUCHER", "ACCEPTANCE_EVIDENCE", "RECTIFICATION_EVIDENCE", "ISSUE_EVIDENCE", "FEEDBACK_IMAGE", "STAFF_HEALTH_CERT");
    private final FileMapper db;
    private final AdminMapper admin;
    private final CurrentUser current;
    private final AuditService audit;
    private final Path uploadDir;

    public FileService(FileMapper db, AdminMapper admin, CurrentUser current, AuditService audit,
                       @Value("${app.upload-dir}") String uploadDir) {
        this.db = db;
        this.admin = admin;
        this.current = current;
        this.audit = audit;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    private String type(byte[] bytes) {
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
        throw bad("仅支持 PNG、JPEG、WebP 图片或 PDF 文件");
    }

    public Map<String, Object> upload(MultipartFile file, String purpose, long canteenId) throws IOException {
        if (!PURPOSES.contains(purpose)) throw bad("附件用途不正确");
        if (!"SUPPLIER_CERT".equals(purpose) && admin.canteen(canteenId) == null) throw bad("食堂不存在");
        if ("FEEDBACK_IMAGE".equals(purpose)) current.requireRole("COMMUNITY");
        else if ("ISSUE_EVIDENCE".equals(purpose)) current.requireRole("INSPECTOR");
        else {
            switch (purpose) {
                case "SUPPLIER_CERT" -> current.requireRole("MANAGER");
                case "STAFF_HEALTH_CERT" -> current.requireRole("STAFF", "PURCHASER", "CHEF", "CLEANER");
                case "BATCH_VOUCHER", "ACCEPTANCE_EVIDENCE" -> current.requireRole("PURCHASER");
                case "RECTIFICATION_EVIDENCE" -> current.requireRole("MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
                default -> throw bad("附件用途不正确");
            }
            if ("STAFF_HEALTH_CERT".equals(purpose)) {
                if (!current.canteens().contains(canteenId)) throw new ApiException(HttpStatus.FORBIDDEN, "只能上传本人所属食堂的健康证明");
            } else if (!"SUPPLIER_CERT".equals(purpose)) current.requireCanteen(canteenId);
        }
        if (file.isEmpty() || file.getSize() > 8L * 1024 * 1024) throw bad("附件大小需在 1 字节至 8 MB 之间");
        byte[] bytes = file.getBytes();
        String contentType = type(bytes);
        if ("FEEDBACK_IMAGE".equals(purpose) && contentType.equals("application/pdf")) throw bad("反馈附件只能上传图片");
        String id = UUID.randomUUID().toString();
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) name = "附件";
        name = name.replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 255) name = name.substring(0, 255);
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(id);
        Files.write(target, bytes);
        Map<String, Object> value = new HashMap<>();
        value.put("id", id);
        value.put("purpose", purpose);
        value.put("canteenId", "SUPPLIER_CERT".equals(purpose) ? null : canteenId);
        value.put("ownerId", current.id());
        value.put("originalName", name);
        value.put("contentType", contentType);
        value.put("sizeBytes", bytes.length);
        try { db.insert(value); }
        catch (RuntimeException ex) { Files.deleteIfExists(target); throw ex; }
        audit.log("UPLOAD_ATTACHMENT", "uploaded_file", null, "id=" + id + ",purpose=" + purpose);
        return Map.of("path", "/api/files/" + id, "name", name, "size", bytes.length, "contentType", contentType);
    }

    public void requireOwned(String path, String purpose, long canteenId) {
        if (path == null || path.isBlank()) return;
        String id = path.startsWith("/api/files/") ? path.substring("/api/files/".length()) : "";
        Map<String, Object> file = db.byId(id);
        if (file == null || !purpose.equals(file.get("purpose"))
                || (!"SUPPLIER_CERT".equals(purpose) && ((Number) file.get("canteen_id")).longValue() != canteenId)
                || ((Number) file.get("owner_id")).longValue() != current.id()) {
            throw bad("附件必须先由当前账号上传，且用途和食堂一致");
        }
    }

    public ResponseEntity<Resource> download(String id) throws IOException {
        Map<String, Object> file = db.byId(id);
        if (file == null) throw new ApiException(HttpStatus.NOT_FOUND, "附件不存在");
        long ownerId = ((Number) file.get("owner_id")).longValue();
        Long canteenId = file.get("canteen_id") instanceof Number number ? number.longValue() : null;
        boolean healthCertificate = "STAFF_HEALTH_CERT".equals(file.get("purpose"));
        boolean allowed = healthCertificate ? canReadHealthCertificate(id, ownerId, canteenId) : current.id() == ownerId || current.has("ADMIN") || current.has("INSPECTOR")
                || (current.has("PRINCIPAL") && !"FEEDBACK_IMAGE".equals(file.get("purpose")));
        if (!allowed && "SUPPLIER_CERT".equals(file.get("purpose"))) {
            allowed = current.has("MANAGER") || current.has("PURCHASER");
        }
        if (!healthCertificate && !allowed && canteenId != null && current.canteens().contains(canteenId) && (current.has("MANAGER") || current.isWorker())) {
            if ("BATCH_VOUCHER".equals(file.get("purpose")) || "ACCEPTANCE_EVIDENCE".equals(file.get("purpose"))) {
                allowed = current.has("MANAGER") || current.has("PURCHASER") || current.has("CHEF");
            } else if ("ISSUE_EVIDENCE".equals(file.get("purpose")) || "RECTIFICATION_EVIDENCE".equals(file.get("purpose"))) {
                Map<String, Object> issue = db.assignedIssue("/api/files/" + id);
                allowed = current.has("MANAGER") || issue != null && issue.get("assigned_to") instanceof Number assignee
                        && assignee.longValue() == current.id();
            } else if ("FEEDBACK_IMAGE".equals(file.get("purpose"))) {
                Map<String, Object> feedback = db.assignedFeedback("/api/files/" + id);
                if (feedback != null) {
                    allowed = current.has("MANAGER") || (feedback.get("assigned_to") != null
                            && ((Number) feedback.get("assigned_to")).longValue() == current.id());
                }
            }
        }
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "无权查看该附件");
        Path path = uploadDir.resolve(id);
        if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "附件文件不存在");
        Resource resource = new UrlResource(path.toUri());
        audit.log("VIEW_ATTACHMENT", "uploaded_file", null, "id=" + id + ",purpose=" + file.get("purpose"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(String.valueOf(file.get("content_type"))))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(((Number) file.get("size_bytes")).longValue())
                .body(resource);
    }

    private boolean canReadHealthCertificate(String id, long ownerId, Long uploadedCanteen) {
        for (Map<String, Object> link : db.healthCertificateLinks("/api/files/" + id)) {
            long subjectId = ((Number) link.get("user_id")).longValue();
            long canteenId = ((Number) link.get("canteen_id")).longValue();
            String status = String.valueOf(link.get("status"));
            boolean inScope = current.canteens().contains(canteenId);
            if (current.isWorker() && subjectId == current.id() && inScope) return true;
            if (current.has("MANAGER") && inScope && Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) return true;
            if ((current.has("ADMIN") || current.has("INSPECTOR") || current.has("PRINCIPAL")) && "APPROVED".equals(status)) return true;
        }
        // A personal upload may be previewed before a draft is saved. Once linked,
        // use the subject/review rules above, including for former manager uploads.
        return current.id() == ownerId && current.isWorker() && uploadedCanteen != null
                && current.canteens().contains(uploadedCanteen)
                && db.healthCertificateUseCount("/api/files/" + id) == 0;
    }
}
