package edu.campus.food.supervision;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.auth.WorkRoles;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import edu.campus.food.file.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final SupervisionMapper db;
    private final UserMapper users;
    private final CurrentUser current;
    private final AuditService audit;
    private final FileService files;

    public FeedbackController(SupervisionMapper db, UserMapper users, CurrentUser current, AuditService audit, FileService files) {
        this.db = db;
        this.users = users;
        this.current = current;
        this.audit = audit;
        this.files = files;
    }

    private boolean visible(Map<String, Object> row) {
        if (current.has("ADMIN") || current.has("INSPECTOR")) return true;
        long canteenId = ((Number) row.get("canteen_id")).longValue();
        if (current.has("MANAGER")) return current.canteens().contains(canteenId);
        if (current.isWorker()) return current.canteens().contains(canteenId)
                && row.get("assigned_to") instanceof Number assignee && assignee.longValue() == current.id();
        if (current.has("COMMUNITY")) return ((Number) row.get("user_id")).longValue() == current.id();
        return false;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.feedbacks().stream().filter(this::visible).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        Map<String, Object> item = db.feedback(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "反馈不存在");
        if (!visible(item)) throw new ApiException(HttpStatus.FORBIDDEN, "无权查看该反馈");
        return item;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        current.requireRole("COMMUNITY");
        long canteenId = Input.id(body, "canteenId");
        String description = Input.text(body, "description", 5000);
        if (db.recentDuplicateFeedback(current.id(), canteenId, description) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "相同反馈已提交，请稍后查看处理进度");
        }
        Map<String, Object> value = new HashMap<>();
        value.put("userId", current.id());
        value.put("canteenId", canteenId);
        value.put("occurredAt", Input.dateTime(body, "occurredAt"));
        value.put("category", Input.text(body, "category", 80));
        value.put("description", description);
        value.put("imagePath", Input.optionalText(body, "imagePath", 500));
        files.requireOwned((String) value.get("imagePath"), "FEEDBACK_IMAGE", canteenId);
        db.addFeedback(value);
        audit.log("CREATE_FEEDBACK", "feedback", ((Number) value.get("id")).longValue(), "canteen=" + canteenId);
        return detail(((Number) value.get("id")).longValue());
    }

    @PostMapping("/{id}/assign")
    public Map<String, Object> assign(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> item = db.feedback(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "反馈不存在");
        long canteenId = ((Number) item.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        long assigneeId = Input.id(body, "assignedTo");
        List<String> roles = users.roles(assigneeId);
        Map<String, Object> assignee = users.byId(assigneeId);
        if (assignee == null || !Boolean.TRUE.equals(assignee.get("active"))
                || !WorkRoles.canBeAssigned(roles)
                || !users.scopes(assigneeId).contains(canteenId)) {
            throw Input.bad("处理人不属于该食堂");
        }
        if (db.assignFeedback(id, assigneeId) == 0) throw Input.bad("只能分派待受理的反馈");
        audit.log("ASSIGN_FEEDBACK", "feedback", id, "assignee=" + assigneeId);
        return detail(id);
    }

    @PostMapping("/{id}/investigate")
    public Map<String, Object> investigate(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");
        Map<String, Object> item = detail(id);
        long canteenId = ((Number) item.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        if (!"PROCESSING".equals(item.get("status"))) throw Input.bad("只能处理进行中的反馈");
        if (current.isWorker() && !current.has("MANAGER")
                && ((Number) item.get("assigned_to")).longValue() != current.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能处理分派给自己的反馈");
        }
        String note = Input.text(body, "note", 5000);
        if (db.investigateFeedback(id, note, current.id()) == 0) throw Input.bad("该反馈已记录调查结果");
        audit.log("INVESTIGATE_FEEDBACK", "feedback", id, "食堂调查处理已记录");
        return detail(id);
    }

    @PostMapping("/{id}/respond")
    public Map<String, Object> respond(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> item = detail(id);
        if (item.get("investigation_note") == null) throw Input.bad("请先记录调查处理结果");
        if (db.respondFeedback(id, Input.text(body, "response", 5000)) == 0) {
            throw Input.bad("只能回复处理中的反馈");
        }
        audit.log("RESPOND_FEEDBACK", "feedback", id, "已回复");
        return detail(id);
    }

    @PostMapping("/{id}/close")
    public Map<String, Object> close(@PathVariable long id) {
        current.requireRole("MANAGER");
        detail(id);
        if (db.closeFeedback(id) == 0) throw Input.bad("只能关闭已回复的反馈");
        audit.log("CLOSE_FEEDBACK", "feedback", id, "已关闭");
        return detail(id);
    }

    @PostMapping("/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable long id) {
        current.requireRole("COMMUNITY");
        Map<String, Object> item = detail(id);
        if (((Number) item.get("user_id")).longValue() != current.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能重新打开本人反馈");
        }
        if (db.reopenFeedback(id) == 0) throw Input.bad("只能重新打开已回复或已关闭的反馈");
        audit.log("REOPEN_FEEDBACK", "feedback", id, "师生要求继续处理");
        return detail(id);
    }
}
