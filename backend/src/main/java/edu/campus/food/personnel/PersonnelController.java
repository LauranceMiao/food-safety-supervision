package edu.campus.food.personnel;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import edu.campus.food.file.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {
    private final PersonnelMapper db;
    private final CurrentUser current;
    private final FileService files;
    private final AuditService audit;

    public PersonnelController(PersonnelMapper db, CurrentUser current, FileService files, AuditService audit) {
        this.db = db;
        this.current = current;
        this.files = files;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        List<Map<String, Object>> rows = current.visibleCanteens(db.all()).stream()
                .filter(row -> row.get("approved_submission_id") instanceof Number).toList();
        if (current.isWorker() && !current.isSupervisor()) {
            return rows.stream().filter(row -> number(row, "user_id") == current.id()).toList();
        }
        return rows;
    }

    @GetMapping("/my")
    public List<Map<String, Object>> mine() {
        requireWorker();
        return db.mine(current.id()).stream().filter(row -> current.canteens().contains(number(row, "canteen_id"))).toList();
    }

    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews() {
        current.requireRole("MANAGER");
        // Management assignments remain mandatory even for an account with another global role.
        return db.reviews().stream().filter(row -> current.canteens().contains(number(row, "canteen_id")))
                .filter(row -> !"DRAFT".equals(row.get("status"))).toList();
    }

    @GetMapping("/candidates/{canteenId}")
    public List<Map<String, Object>> candidates(@PathVariable long canteenId) {
        current.requireRole("ADMIN", "INSPECTOR", "MANAGER");
        current.requireCanteen(canteenId);
        return db.candidates(canteenId);
    }

    @PutMapping("/{userId}/canteens/{canteenId}")
    public Map<String, Object> save(@PathVariable long userId, @PathVariable long canteenId,
                                    @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        throw conflict("人员健康档案需由工作人员本人填写并提交，总管只能审核");
    }

    @PostMapping("/my/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createDraft(@RequestBody Map<String, Object> body) {
        requireWorker();
        long canteenId = Input.id(body, "canteenId");
        requireOwnCanteen(canteenId);
        db.lockUser(current.id());
        if (db.workerAt(current.id(), canteenId) != 1) throw Input.bad("当前账号未分配该食堂的工作人员职责");
        Map<String, Object> latest = db.latest(current.id(), canteenId);
        if (latest != null && ("DRAFT".equals(latest.get("status")) || "PENDING".equals(latest.get("status")))) {
            throw conflict("该食堂已有草稿或待审核档案，请继续编辑草稿或等待审核");
        }
        Map<String, Object> value = draftFields(body, canteenId);
        value.put("userId", current.id());
        db.createDraft(value);
        long id = number(value, "id");
        audit.log("SAVE_STAFF_PROFILE_DRAFT", "staff_profile_submission", id, "version=1");
        return db.submission(id);
    }

    @PutMapping("/my/submissions/{id}")
    @Transactional
    public Map<String, Object> updateDraft(@PathVariable long id, @RequestBody Map<String, Object> body) {
        requireWorker();
        Map<String, Object> existing = ownLocked(id);
        requireStatus(existing, "DRAFT");
        long canteenId = number(existing, "canteen_id");
        if (Input.id(body, "canteenId") != canteenId) throw Input.bad("草稿所属食堂不能修改，请为相应食堂新建档案");
        long version = requireVersion(existing, body);
        Map<String, Object> value = draftFields(body, canteenId);
        value.put("id", id);
        value.put("userId", current.id());
        value.put("version", version);
        if (db.updateDraft(value) != 1) throw changed();
        audit.log("SAVE_STAFF_PROFILE_DRAFT", "staff_profile_submission", id, "version=" + (version + 1));
        return db.submission(id);
    }

    @PostMapping("/my/submissions/{id}/submit")
    @Transactional
    public Map<String, Object> submit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        requireWorker();
        Map<String, Object> value = ownLocked(id);
        requireStatus(value, "DRAFT");
        long version = requireVersion(value, body);
        long canteenId = number(value, "canteen_id");
        if (db.workerAt(current.id(), canteenId) != 1) throw Input.bad("当前账号未分配该食堂的工作人员职责");
        required(value, "real_name", "姓名");
        required(value, "position_name", "岗位");
        required(value, "health_cert_no", "健康证编号");
        if (value.get("health_expires_on") == null) throw Input.bad("请填写健康证到期日期");
        String path = required(value, "certificate_path", "健康证明附件");
        files.requireOwned(path, "STAFF_HEALTH_CERT", canteenId);
        if (db.submit(id, current.id(), version) != 1) throw changed();
        audit.log("SUBMIT_STAFF_PROFILE", "staff_profile_submission", id, "version=" + (version + 1));
        return db.submission(id);
    }

    @PostMapping("/submissions/{id}/review")
    @Transactional
    public Map<String, Object> review(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> existing = locked(id);
        long canteenId = number(existing, "canteen_id");
        if (!current.canteens().contains(canteenId)) throw new ApiException(HttpStatus.FORBIDDEN, "只能审核本人管理食堂的健康档案");
        if (number(existing, "user_id") == current.id()) throw new ApiException(HttpStatus.FORBIDDEN, "不能审核本人提交的健康档案");
        requireStatus(existing, "PENDING");
        long version = requireVersion(existing, body);
        if (db.workerAt(number(existing, "user_id"), canteenId) != 1) throw Input.bad("该人员已不属于该食堂的有效工作人员，请先核实账号职责");
        String decision = Input.oneOf(body, "decision", "APPROVED", "REJECTED");
        String note = Input.optionalText(body, "note", 500);
        if ("REJECTED".equals(decision) && note.isBlank()) throw Input.bad("审核不通过时必须填写退回理由");
        Map<String, Object> value = new HashMap<>();
        value.put("id", id);
        value.put("version", version);
        value.put("decision", decision);
        value.put("reviewedBy", current.id());
        value.put("reviewNote", note);
        if (db.review(value) != 1) throw changed();
        if ("APPROVED".equals(decision) && db.approveSnapshot(id, current.id()) < 1) {
            throw conflict("审核结果同步失败，请刷新后重试");
        }
        audit.log("APPROVED".equals(decision) ? "APPROVE_STAFF_PROFILE" : "REJECT_STAFF_PROFILE",
                "staff_profile_submission", id, "version=" + (version + 1) + ",decision=" + decision);
        return db.submission(id);
    }

    private Map<String, Object> draftFields(Map<String, Object> body, long canteenId) {
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("realName", Input.optionalText(body, "realName", 80));
        value.put("contactPhone", Input.optionalText(body, "contactPhone", 30));
        value.put("gender", body.get("gender") == null || String.valueOf(body.get("gender")).isBlank()
                ? "UNSPECIFIED" : Input.oneOf(body, "gender", "UNSPECIFIED", "MALE", "FEMALE"));
        value.put("positionName", Input.optionalText(body, "positionName", 80));
        value.put("healthCertNo", Input.optionalText(body, "healthCertNo", 100));
        value.put("healthExpiresOn", Input.optionalDate(body, "healthExpiresOn"));
        String path = Input.optionalText(body, "certificatePath", 500);
        files.requireOwned(path, "STAFF_HEALTH_CERT", canteenId);
        value.put("certificatePath", path);
        value.put("lastTrainingOn", Input.optionalDate(body, "lastTrainingOn"));
        value.put("trainingNote", Input.optionalText(body, "trainingNote", 1000));
        return value;
    }

    private void requireWorker() { current.requireRole("STAFF", "PURCHASER", "CHEF", "CLEANER"); }

    private void requireOwnCanteen(long canteenId) {
        if (!current.canteens().contains(canteenId)) throw new ApiException(HttpStatus.FORBIDDEN, "只能填写本人所属食堂的健康档案");
    }

    private Map<String, Object> locked(long id) {
        Map<String, Object> value = db.lockSubmission(id);
        if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "健康档案申请不存在");
        return value;
    }

    private Map<String, Object> ownLocked(long id) {
        Map<String, Object> value = locked(id);
        if (number(value, "user_id") != current.id()) throw new ApiException(HttpStatus.FORBIDDEN, "只能修改本人健康档案");
        requireOwnCanteen(number(value, "canteen_id"));
        return value;
    }

    private long requireVersion(Map<String, Object> value, Map<String, Object> body) {
        long version = Input.id(body, "version");
        if (version != number(value, "version")) throw changed();
        return version;
    }

    private void requireStatus(Map<String, Object> value, String status) {
        if (!status.equals(value.get("status"))) throw conflict("档案状态已变化，请刷新后重试；已提交档案不能直接修改");
    }

    private String required(Map<String, Object> value, String key, String label) {
        String text = value.get(key) == null ? "" : String.valueOf(value.get(key)).trim();
        if (text.isBlank()) throw Input.bad("请填写或上传" + label);
        return text;
    }

    private long number(Map<String, Object> value, String key) { return ((Number) value.get(key)).longValue(); }
    private ApiException changed() { return conflict("档案已被修改或审核，请刷新后重试"); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}
