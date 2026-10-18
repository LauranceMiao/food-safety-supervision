package edu.campus.food.reminder;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.Input;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderMapper db;
    private final CurrentUser current;

    public ReminderController(ReminderMapper db, CurrentUser current) {
        this.db = db;
        this.current = current;
    }

    private void add(List<Map<String, Object>> reminders, String key, String title, String target, Object dueAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("key", key);
        row.put("title", title);
        row.put("target", target);
        row.put("dueAt", dueAt);
        reminders.add(row);
    }

    private boolean scoped(Map<String, Object> row) {
        long canteenId = ((Number) row.get("canteen_id")).longValue();
        return current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR")
                || current.canteens().contains(canteenId);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        long userId = current.id();
        List<Map<String, Object>> reminders = new ArrayList<>();
        if (current.isSupervisor() || current.has("PURCHASER")) {
            for (Map<String, Object> row : db.suppliers()) {
                add(reminders, "SUPPLIER:" + row.get("id") + ":" + row.get("license_expires_on"),
                        "供应商证照临期或过期：" + row.get("name"), "/suppliers", row.get("license_expires_on"));
            }
        }
        if (!current.has("COMMUNITY") || current.roles().size() > 1) {
            for (Map<String, Object> row : db.batches()) if (scoped(row)
                    && (current.isSupervisor() || current.has("PURCHASER") || current.has("CHEF"))) {
                add(reminders, "BATCH:" + row.get("id") + ":" + row.get("expires_on"),
                        "食材批次临期或过期：" + row.get("batch_code"), "/batches", row.get("expires_on"));
            }
            for (Map<String, Object> row : db.overdueIssues()) if (scoped(row)
                    && (current.isSupervisor() || current.isWorker()
                    && ((Number) row.get("assigned_to")).longValue() == userId)) {
                add(reminders, "ISSUE:" + row.get("id") + ":OVERDUE", "整改问题已逾期：#" + row.get("id"),
                        "/issues", row.get("due_at"));
            }
            for (Map<String, Object> row : db.inspections()) {
                if (current.has("ADMIN") || current.has("PRINCIPAL")
                        || current.has("INSPECTOR") && ((Number) row.get("inspector_id")).longValue() == userId
                        || current.has("MANAGER") && current.canteens().contains(((Number) row.get("canteen_id")).longValue())) {
                    add(reminders, "INSPECTION:" + row.get("id"), "即将检查：任务 #" + row.get("id"),
                            "/inspections", row.get("scheduled_at"));
                }
            }
            for (Map<String, Object> row : db.healthCertificates()) {
                if (scoped(row) && (current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR")
                        || current.has("MANAGER") || ((Number) row.get("user_id")).longValue() == userId)) {
                    add(reminders, "HEALTH:" + row.get("user_id") + ":" + row.get("canteen_id") + ":" + row.get("health_expires_on"),
                            "健康证明临期或过期：" + row.get("display_name"), "/personnel", row.get("health_expires_on"));
                }
            }
            for (Map<String, Object> row : db.healthSubmissions()) {
                boolean inScope = current.canteens().contains(((Number) row.get("canteen_id")).longValue());
                if (!inScope) continue;
                if ("PENDING".equals(row.get("status")) && current.has("MANAGER")) {
                    add(reminders, "HEALTH_REVIEW:" + row.get("id"), "健康档案待审核：" + row.get("real_name"),
                            "/personnel", row.get("submitted_at"));
                }
                if (current.isWorker() && ((Number) row.get("user_id")).longValue() == userId) {
                    if ("REJECTED".equals(row.get("status"))) {
                        add(reminders, "HEALTH_RETURNED:" + row.get("id"), "健康档案已退回，请查看原因并重新提交",
                                "/personnel", row.get("reviewed_at"));
                    } else if ("APPROVED".equals(row.get("status"))) {
                        add(reminders, "HEALTH_APPROVED:" + row.get("id"), "你的健康档案已审核通过",
                                "/personnel", row.get("reviewed_at"));
                    }
                }
            }
        }
        for (Map<String, Object> row : db.feedbacks()) {
            long canteenId = ((Number) row.get("canteen_id")).longValue();
            boolean managerForCanteen = current.has("MANAGER") && current.canteens().contains(canteenId);
            if ("NEW".equals(row.get("status")) && managerForCanteen) {
                add(reminders, "FEEDBACK:" + row.get("id") + ":NEW", "本食堂新反馈待受理：#" + row.get("id"),
                        "/feedback", null);
            }
            if ("PROCESSING".equals(row.get("status")) && managerForCanteen
                    && row.get("investigation_note") != null) {
                add(reminders, "FEEDBACK:" + row.get("id") + ":REPLY", "反馈调查完成，等待食堂回复：#" + row.get("id"),
                        "/feedback", null);
            }
            if ("PROCESSING".equals(row.get("status")) && managerForCanteen
                    && row.get("investigation_note") == null
                    && row.get("assigned_to") instanceof Number assignee && assignee.longValue() == userId) {
                add(reminders, "FEEDBACK:" + row.get("id") + ":INVESTIGATE", "分派给你的反馈待调查：#" + row.get("id"),
                        "/feedback", null);
            }
            if ("PROCESSING".equals(row.get("status")) && row.get("investigation_note") == null
                    && current.isWorker() && !current.has("MANAGER") && current.canteens().contains(canteenId)
                    && row.get("assigned_to") instanceof Number assignee && assignee.longValue() == userId) {
                add(reminders, "FEEDBACK:" + row.get("id") + ":INVESTIGATE", "分派给你的反馈待调查：#" + row.get("id"),
                        "/feedback", null);
            }
            if ("RESPONDED".equals(row.get("status")) && current.has("COMMUNITY")
                    && ((Number) row.get("user_id")).longValue() == userId) {
                add(reminders, "FEEDBACK:" + row.get("id") + ":RESPONDED", "你的反馈已有回复：#" + row.get("id"),
                        "/feedback", null);
            }
        }
        Set<String> read = Set.copyOf(db.readKeys(userId));
        for (Map<String, Object> row : reminders) row.put("read", read.contains(row.get("key")));
        return reminders.stream().limit(100).toList();
    }

    @PostMapping("/read")
    public Map<String, Boolean> read(@RequestBody Map<String, Object> body) {
        String key = Input.text(body, "key", 150);
        if (list().stream().noneMatch(item -> key.equals(item.get("key")))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "提醒不存在或无权查看");
        }
        db.markRead(current.id(), key);
        return Map.of("ok", true);
    }
}
