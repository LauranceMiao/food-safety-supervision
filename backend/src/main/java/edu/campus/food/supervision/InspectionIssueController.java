package edu.campus.food.supervision;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.auth.WorkRoles;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.catalog.CatalogMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import edu.campus.food.file.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class InspectionIssueController {
    private final SupervisionMapper db;
    private final AdminMapper admin;
    private final CatalogMapper catalog;
    private final UserMapper users;
    private final CurrentUser current;
    private final AuditService audit;
    private final FileService files;

    public InspectionIssueController(SupervisionMapper db, AdminMapper admin, CatalogMapper catalog, UserMapper users,
                                     CurrentUser current, AuditService audit, FileService files) {
        this.db = db;
        this.admin = admin;
        this.catalog = catalog;
        this.users = users;
        this.current = current;
        this.audit = audit;
        this.files = files;
    }

    @GetMapping("/inspectors")
    public List<Map<String, Object>> inspectors() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        return users.inspectors();
    }

    @GetMapping("/assignees/{canteenId}")
    public List<Map<String, Object>> assignees(@PathVariable long canteenId) {
        current.requireRole("INSPECTOR", "MANAGER");
        if (current.has("MANAGER") && !current.has("INSPECTOR")) current.requireCanteen(canteenId);
        return users.all().stream().filter(user -> {
            long id = ((Number) user.get("id")).longValue();
            List<String> roles = users.roles(id);
            return Boolean.TRUE.equals(user.get("active")) && users.scopes(id).contains(canteenId)
                    && WorkRoles.canBeAssigned(roles);
        }).map(user -> Map.<String, Object>of("id", user.get("id"), "display_name", user.get("display_name")))
                .toList();
    }

    @GetMapping("/inspections")
    public List<Map<String, Object>> inspections() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        return current.visibleCanteens(db.inspections());
    }

    @GetMapping("/inspections/{id}")
    public Map<String, Object> inspectionDetail(@PathVariable long id) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        Map<String, Object> task = db.inspection(id);
        if (task == null) throw new ApiException(HttpStatus.NOT_FOUND, "检查任务不存在");
        current.requireCanteen(((Number) task.get("canteen_id")).longValue());
        Map<String, Object> detail = new HashMap<>(task);
        detail.put("results", db.inspectionResults(id));
        return detail;
    }

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addInspection(@RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR");
        long inspectorId = Input.id(body, "inspectorId");
        if (!users.roles(inspectorId).contains("INSPECTOR")) throw Input.bad("指定人员不是监管人员");
        long templateId = Input.id(body, "templateId");
        Map<String, Object> template = admin.template(templateId);
        if (template == null || !Boolean.TRUE.equals(template.get("active")) || admin.templateItems(templateId).isEmpty()) {
            throw Input.bad("检查模板不存在、未启用或没有检查项目");
        }
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", Input.id(body, "canteenId"));
        value.put("inspectorId", inspectorId);
        value.put("templateId", templateId);
        value.put("scheduledAt", Input.dateTime(body, "scheduledAt"));
        value.put("note", Input.optionalText(body, "note", 3000));
        value.put("createdBy", current.id());
        db.addInspection(value);
        audit.log("CREATE_INSPECTION", "inspection", ((Number) value.get("id")).longValue(), "canteen=" + value.get("canteenId"));
        return db.inspection(((Number) value.get("id")).longValue());
    }

    @PostMapping("/inspections/{id}/start")
    public Map<String, Object> startInspection(@PathVariable long id) {
        current.requireRole("INSPECTOR");
        Map<String, Object> task = inspectionAssignedToMe(id);
        if (db.startInspection(id) == 0) throw Input.bad("只能开始待执行的检查任务");
        audit.log("START_INSPECTION", "inspection", id, "canteen=" + task.get("canteen_id"));
        return db.inspection(id);
    }

    @PostMapping("/inspections/{id}/complete")
    @Transactional
    public Map<String, Object> completeInspection(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR");
        Map<String, Object> task = inspectionAssignedToMe(id);
        if (!"IN_PROGRESS".equals(task.get("status"))) throw Input.bad("只能完成进行中的检查任务");
        Object templateValue = task.get("template_id");
        if (!(templateValue instanceof Number)) throw Input.bad("该检查任务尚未绑定检查模板");
        List<Map<String, Object>> expected = admin.templateItems(((Number) templateValue).longValue()).stream()
                .filter(item -> Boolean.TRUE.equals(item.get("active"))).toList();
        if (expected.isEmpty()) throw Input.bad("检查模板没有可用项目");
        if (!(body.get("results") instanceof List<?> rawResults)) throw Input.bad("请逐项填写检查结果");
        List<Map<String, Object>> submitted = new ArrayList<>();
        for (Object raw : rawResults) {
            if (!(raw instanceof Map<?, ?> source)) throw Input.bad("检查结果格式不正确");
            Map<String, Object> item = new HashMap<>();
            source.forEach((key, value) -> item.put(String.valueOf(key), value));
            submitted.add(item);
        }
        Set<Long> expectedIds = expected.stream().map(item -> ((Number) item.get("id")).longValue()).collect(Collectors.toSet());
        Set<Long> submittedIds = submitted.stream().map(item -> Input.id(item, "itemId")).collect(Collectors.toSet());
        if (!expectedIds.equals(submittedIds) || submitted.size() != expected.size()) throw Input.bad("必须逐项填写全部检查项目");
        for (Map<String, Object> row : submitted) {
            long itemId = Input.id(row, "itemId");
            Map<String, Object> templateItem = expected.stream().filter(item -> ((Number) item.get("id")).longValue() == itemId)
                    .findFirst().orElseThrow();
            String result = Input.oneOf(row, "result", "PASS", "FAIL", "NA");
            String note = Input.optionalText(row, "note", 500);
            if ("FAIL".equals(result) && note.isBlank()) throw Input.bad("异常检查项必须填写说明");
            db.addInspectionResult(Map.of("inspectionId", id, "itemId", itemId,
                    "itemTitle", templateItem.get("title"), "result", result, "note", note));
        }
        if (db.completeInspection(id, Input.optionalText(body, "note", 3000)) == 0) throw Input.bad("检查任务状态已改变");
        audit.log("COMPLETE_INSPECTION", "inspection", id, "完成检查");
        return inspectionDetail(id);
    }

    @PostMapping("/inspections/{id}/cancel")
    public Map<String, Object> cancelInspection(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR");
        inspectionAssignedToMe(id);
        if (db.cancelInspection(id, Input.text(body, "reason", 500)) == 0) throw Input.bad("只能取消待执行的检查任务");
        audit.log("CANCEL_INSPECTION", "inspection", id, "取消任务");
        return db.inspection(id);
    }

    private Map<String, Object> inspectionAssignedToMe(long id) {
        Map<String, Object> task = db.inspection(id);
        if (task == null) throw new ApiException(HttpStatus.NOT_FOUND, "检查任务不存在");
        if (((Number) task.get("inspector_id")).longValue() != current.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能处理分配给自己的检查任务");
        }
        return task;
    }

    @GetMapping("/issues")
    public List<Map<String, Object>> issues() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        List<Map<String, Object>> visible = current.visibleCanteens(db.issues());
        if (limitedStaff()) return visible.stream()
                .filter(row -> ((Number) row.get("assigned_to")).longValue() == current.id()).toList();
        return visible;
    }

    @GetMapping("/issues/{id}")
    public Map<String, Object> issueDetail(@PathVariable long id) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        Map<String, Object> issue = db.issue(id);
        if (issue == null) throw new ApiException(HttpStatus.NOT_FOUND, "问题单不存在");
        current.requireCanteen(((Number) issue.get("canteen_id")).longValue());
        if (limitedStaff() && ((Number) issue.get("assigned_to")).longValue() != current.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能查看分派给自己的问题单");
        }
        Map<String, Object> result = new HashMap<>(issue);
        result.put("rectifications", db.rectifications(id));
        result.put("events", db.issueEvents(id));
        result.put("oversights", db.oversights(id));
        return result;
    }

    private boolean limitedStaff() {
        return current.isWorker() && !current.isSupervisor();
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> addIssue(@RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR");
        long canteenId = Input.id(body, "canteenId");
        long assignedTo = Input.id(body, "assignedTo");
        if (!WorkRoles.canBeAssigned(users.roles(assignedTo))
                || !users.scopes(assignedTo).contains(canteenId)) throw Input.bad("整改责任人不属于该食堂");
        String sourceType = Input.oneOf(body, "sourceType", "MANUAL", "INSPECTION", "BATCH", "FEEDBACK", "DAILY_CHECK");
        Long sourceId = Input.optionalId(body, "sourceId");
        validateSource(sourceType, sourceId, canteenId);
        LocalDateTime dueAt = Input.dateTime(body, "dueAt");
        if (!dueAt.isAfter(LocalDateTime.now())) throw Input.bad("整改期限必须晚于当前时间");
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("sourceType", sourceType);
        value.put("sourceId", sourceId);
        value.put("category", Input.text(body, "category", 80));
        value.put("severity", Input.oneOf(body, "severity", "LOW", "MEDIUM", "HIGH"));
        value.put("description", Input.text(body, "description", 5000));
        value.put("location", Input.optionalText(body, "location", 200));
        value.put("evidencePath", Input.optionalText(body, "evidencePath", 500));
        files.requireOwned((String) value.get("evidencePath"), "ISSUE_EVIDENCE", canteenId);
        value.put("assignedTo", assignedTo);
        value.put("dueAt", dueAt);
        value.put("needsReport", Boolean.TRUE.equals(body.get("needsReport")));
        value.put("reportNote", Input.optionalText(body, "reportNote", 3000));
        if (Boolean.TRUE.equals(value.get("needsReport")) && String.valueOf(value.get("reportNote")).isBlank()) {
            throw Input.bad("重大异常需填写手工上报说明");
        }
        value.put("createdBy", current.id());
        db.addIssue(value);
        long id = ((Number) value.get("id")).longValue();
        if ("FEEDBACK".equals(sourceType)) db.linkFeedbackIssue(sourceId, id);
        if (Boolean.TRUE.equals(value.get("needsReport"))) {
            db.addIssueEvent(Map.of("issueId", id, "actorId", current.id(), "eventType", "DISCOVERED",
                    "note", "发现重大异常：" + value.get("reportNote")));
        }
        audit.log("CREATE_ISSUE", "issue", id, "severity=" + value.get("severity") + ",source=" + sourceType);
        return issueDetail(id);
    }

    @PostMapping("/issues/{id}/events")
    public Map<String, Object> addIssueEvent(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR", "MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        Map<String, Object> issue = db.issue(id);
        if (issue == null) throw new ApiException(HttpStatus.NOT_FOUND, "问题单不存在");
        current.requireCanteen(((Number) issue.get("canteen_id")).longValue());
        if (limitedStaff() && ((Number) issue.get("assigned_to")).longValue() != current.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能处理分派给自己的问题单");
        }
        if (!Boolean.TRUE.equals(issue.get("needs_report"))) throw Input.bad("仅重大异常问题单可记录处置时间线");
        String type = Input.oneOf(body, "eventType", "REPORTED", "MEASURE", "FOLLOW_UP");
        if ("REPORTED".equals(type) && !current.has("INSPECTOR")) throw new ApiException(HttpStatus.FORBIDDEN, "仅监管人员可记录上报动作");
        Map<String, Object> event = Map.of("issueId", id, "actorId", current.id(), "eventType", type,
                "note", Input.text(body, "note", 3000));
        db.addIssueEvent(event);
        audit.log("ADD_ISSUE_EVENT", "issue", id, type + " " + event.get("note"));
        return issueDetail(id);
    }

    @PostMapping("/issues/{id}/oversights")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addOversight(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("PRINCIPAL");
        if (db.issue(id) == null) throw new ApiException(HttpStatus.NOT_FOUND, "问题单不存在");
        Map<String, Object> value = new HashMap<>();
        value.put("issueId", id);
        value.put("principalId", current.id());
        value.put("note", Input.text(body, "note", 3000));
        db.addOversight(value);
        audit.log("CREATE_OVERSIGHT", "issue_oversight", ((Number) value.get("id")).longValue(), "issue=" + id);
        return issueDetail(id);
    }

    @PostMapping("/oversights/{id}/close")
    public Map<String, Object> closeOversight(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("PRINCIPAL");
        Map<String, Object> oversight = db.oversight(id);
        if (oversight == null) throw new ApiException(HttpStatus.NOT_FOUND, "督办记录不存在");
        if (db.closeOversight(id, Input.text(body, "note", 3000)) == 0) throw Input.bad("该督办已结束");
        audit.log("CLOSE_OVERSIGHT", "issue_oversight", id, "督办已结束");
        return issueDetail(((Number) oversight.get("issue_id")).longValue());
    }

    private void validateSource(String type, Long id, long canteenId) {
        if ("MANUAL".equals(type)) {
            if (id != null) throw Input.bad("手工问题单不需要来源编号");
            return;
        }
        if (id == null) throw Input.bad("请选择来源记录");
        Map<String, Object> source = switch (type) {
            case "INSPECTION" -> db.inspection(id);
            case "BATCH" -> catalog.batch(id);
            case "FEEDBACK" -> db.feedback(id);
            case "DAILY_CHECK" -> db.dailyCheck(id);
            default -> null;
        };
        if (source == null || ((Number) source.get("canteen_id")).longValue() != canteenId) {
            throw Input.bad("来源记录不存在或不属于该食堂");
        }
    }

    @PostMapping("/issues/{id}/rectifications")
    @Transactional
    public Map<String, Object> submitRectification(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        Map<String, Object> issue = db.lockIssue(id);
        if (issue == null) throw new ApiException(HttpStatus.NOT_FOUND, "问题单不存在");
        long canteenId = ((Number) issue.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        if (((Number) issue.get("assigned_to")).longValue() != current.id() && !current.has("MANAGER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅责任人或所属食堂总管可提交整改");
        }
        if (!"OPEN".equals(issue.get("status"))) throw Input.bad("该问题当前不能提交整改");
        Map<String, Object> value = new HashMap<>();
        value.put("issueId", id);
        value.put("submitterId", current.id());
        value.put("details", Input.text(body, "details", 5000));
        value.put("evidencePath", Input.optionalText(body, "evidencePath", 500));
        files.requireOwned((String) value.get("evidencePath"), "RECTIFICATION_EVIDENCE", canteenId);
        db.addRectification(value);
        db.setIssueStatus(id, "REVIEW");
        audit.log("SUBMIT_RECTIFICATION", "issue", id, "rectification=" + value.get("id"));
        return issueDetail(id);
    }

    @PostMapping("/issues/{id}/review")
    @Transactional
    public Map<String, Object> review(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("INSPECTOR");
        Map<String, Object> issue = db.lockIssue(id);
        if (issue == null) throw new ApiException(HttpStatus.NOT_FOUND, "问题单不存在");
        if (!"REVIEW".equals(issue.get("status"))) throw Input.bad("该问题没有待复核的整改");
        Map<String, Object> rectification = db.latestRectification(id);
        if (rectification == null || rectification.get("review_result") != null) throw Input.bad("待复核记录不存在");
        if (((Number) rectification.get("submitter_id")).longValue() == current.id()) throw Input.bad("不能复核本人提交的整改");
        String result = Input.oneOf(body, "result", "APPROVED", "REJECTED");
        String note = Input.text(body, "note", 3000);
        Map<String, Object> values = Map.of("id", rectification.get("id"), "result", result,
                "reviewedBy", current.id(), "note", note);
        db.reviewRectification(values);
        db.setIssueStatus(id, "APPROVED".equals(result) ? "CLOSED" : "OPEN");
        audit.log("REVIEW_RECTIFICATION", "issue", id, result + " " + note);
        return issueDetail(id);
    }
}
