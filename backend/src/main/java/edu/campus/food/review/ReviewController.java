package edu.campus.food.review;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import edu.campus.food.report.ReportMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewMapper db;
    private final ReportMapper reports;
    private final AdminMapper admin;
    private final CurrentUser current;
    private final AuditService audit;
    private final JsonMapper json;

    public ReviewController(ReviewMapper db, ReportMapper reports, AdminMapper admin, CurrentUser current,
                            AuditService audit, JsonMapper json) {
        this.db = db;
        this.reports = reports;
        this.admin = admin;
        this.current = current;
        this.audit = audit;
        this.json = json;
    }

    private boolean visible(Map<String, Object> row) {
        if (current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR")) return true;
        Object scope = row.get("canteen_id");
        return scope instanceof Number number && current.canteens().contains(number.longValue());
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        return db.all().stream().filter(this::visible).toList();
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String type = Input.oneOf(body, "periodType", "WEEK", "MONTH");
        LocalDate start = Input.date(body, "periodStart");
        LocalDate end = "WEEK".equals(type) ? start.plusDays(6) : start.plusMonths(1).minusDays(1);
        if ("WEEK".equals(type) && start.getDayOfWeek() != DayOfWeek.MONDAY
                || "MONTH".equals(type) && start.getDayOfMonth() != 1) {
            throw Input.bad("周报必须从周一开始，月报必须从每月1日开始");
        }
        if (end.isAfter(LocalDate.now()) || start.isBefore(LocalDate.now().minusYears(2))) {
            throw Input.bad("只能生成最近两年内已结束周期的报告草稿");
        }
        Long canteenId = Input.optionalId(body, "canteenId");
        if (canteenId == null) current.requireRole("PRINCIPAL", "ADMIN", "INSPECTOR");
        else {
            current.requireRole("MANAGER", "PRINCIPAL", "ADMIN", "INSPECTOR");
            current.requireCanteen(canteenId);
            if (admin.canteen(canteenId) == null) throw new ApiException(HttpStatus.NOT_FOUND, "食堂不存在");
        }
        List<Map<String, Object>> targets = admin.canteens().stream()
                .filter(row -> canteenId == null || ((Number) row.get("id")).longValue() == canteenId).toList();
        int inspections = 0, issues = 0, closed = 0, overdue = 0, rejected = 0, feedback = 0;
        LocalDate next = end.plusDays(1);
        for (Map<String, Object> target : targets) {
            long id = ((Number) target.get("id")).longValue();
            inspections += reports.completedInspections(id, start, next);
            issues += reports.issues(id, start, next);
            closed += reports.closedIssues(id, start, next);
            overdue += reports.overdueIssues(id, start, next);
            rejected += reports.rejectedBatches(id, start, next);
            feedback += reports.feedbacks(id, start, next);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inspectionCount", inspections);
        summary.put("issueCount", issues);
        summary.put("closedIssueCount", closed);
        summary.put("overdueIssueCount", overdue);
        summary.put("rejectedBatchCount", rejected);
        summary.put("feedbackCount", feedback);
        summary.put("unclosedIssueCount", issues - closed);
        Map<String, Object> value = new HashMap<>();
        value.put("periodType", type);
        value.put("periodStart", start);
        value.put("periodEnd", end);
        value.put("canteenId", canteenId);
        try { value.put("summaryJson", json.writeValueAsString(summary)); }
        catch (Exception ex) { throw new IllegalStateException("报告草稿无法生成", ex); }
        value.put("createdBy", current.id());
        db.add(value);
        long id = ((Number) value.get("id")).longValue();
        audit.log("GENERATE_PERIOD_REVIEW", "period_review", id, type + ",scope=" + canteenId);
        return db.one(id);
    }

    @PostMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> review = db.one(id);
        if (review == null) throw new ApiException(HttpStatus.NOT_FOUND, "周期报告不存在");
        if (review.get("canteen_id") == null) current.requireRole("PRINCIPAL", "ADMIN");
        else {
            current.requireRole("MANAGER", "PRINCIPAL", "ADMIN");
            current.requireCanteen(((Number) review.get("canteen_id")).longValue());
        }
        String conclusion = Input.text(body, "conclusion", 5000);
        if (db.confirm(id, conclusion, current.id()) == 0) throw Input.bad("只有草稿可以确认");
        audit.log("CONFIRM_PERIOD_REVIEW", "period_review", id, conclusion);
        return db.one(id);
    }
}
