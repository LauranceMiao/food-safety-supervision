package edu.campus.food.algorithm;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.supervision.SupervisionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScheduleService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final AlgorithmMapper db;
    private final AdminMapper admin;
    private final UserMapper users;
    private final SupervisionMapper supervision;
    private final RiskService risk;
    private final CurrentUser current;
    private final AuditService audit;

    public ScheduleService(AlgorithmMapper db, AdminMapper admin, UserMapper users, SupervisionMapper supervision,
                           RiskService risk, CurrentUser current, AuditService audit) {
        this.db = db;
        this.admin = admin;
        this.users = users;
        this.supervision = supervision;
        this.risk = risk;
        this.current = current;
        this.audit = audit;
    }

    private LocalDate asDate(Object value) {
        if (value instanceof LocalDate local) return local;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime local) return local;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private void checkWeek(LocalDate start) {
        if (start.getDayOfWeek() != DayOfWeek.MONDAY || start.isBefore(LocalDate.now(ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "排程开始日期须为今天或之后的星期一");
        }
        if (start.isAfter(LocalDate.now(ZONE).plusDays(90))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能生成未来90天内的排程");
        }
    }

    public List<Map<String, Object>> suggestions(LocalDate start) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR");
        return db.suggestions(start, start.plusDays(6));
    }

    public List<Map<String, Object>> availability(LocalDate start) {
        current.requireRole("ADMIN", "INSPECTOR");
        return db.availability(start, start.plusDays(6));
    }

    @Transactional
    public void setAvailability(long inspectorId, LocalDate date, boolean available, int maxTasks) {
        current.requireRole("ADMIN", "INSPECTOR");
        if (!current.has("ADMIN") && current.id() != inspectorId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能设置自己的可用时间");
        }
        if (!users.roles(inspectorId).contains("INSPECTOR")) throw new ApiException(HttpStatus.BAD_REQUEST, "该账号不是监管人员");
        if (date.isBefore(LocalDate.now(ZONE)) || maxTasks < 1 || maxTasks > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "日期或每日容量不正确");
        }
        db.upsertAvailability(Map.of("inspectorId", inspectorId, "date", date,
                "available", available, "maxTasks", maxTasks));
        audit.log("SET_INSPECTOR_AVAILABILITY", "app_user", inspectorId, date + " available=" + available);
    }

    @Transactional
    public Map<String, Object> generate(LocalDate start) {
        current.requireRole("INSPECTOR");
        checkWeek(start);
        LocalDate end = start.plusDays(6);
        if (!db.suggestions(start, end).isEmpty()) {
            return Map.of("suggestions", db.suggestions(start, end), "message", "该周已有排程建议；可逐项调整或确认");
        }
        List<Map<String, Object>> inspectorRows = users.inspectors();
        if (inspectorRows.isEmpty()) return Map.of("suggestions", List.of(), "message", "尚未创建监管人员账号");
        Map<String, Map<String, Object>> availability = new HashMap<>();
        for (Map<String, Object> row : db.availability(start, end)) {
            availability.put(asDate(row.get("available_date")) + ":" + row.get("inspector_id"), row);
        }
        Map<String, Integer> existingCounts = new HashMap<>();
        for (Map<String, Object> row : db.existingTasks(start, end)) {
            existingCounts.put(asDate(row.get("task_date")) + ":" + row.get("inspector_id"),
                    ((Number) row.get("task_count")).intValue());
        }
        List<SchedulePlanner.Slot> slots = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            LocalDate date = start.plusDays(day);
            for (Map<String, Object> inspector : inspectorRows) {
                long inspectorId = ((Number) inspector.get("id")).longValue();
                String key = date + ":" + inspectorId;
                Map<String, Object> entry = availability.get(key);
                if (entry != null && !Boolean.TRUE.equals(entry.get("available"))) continue;
                int max = entry == null ? 2 : ((Number) entry.get("max_tasks")).intValue();
                int remaining = max - existingCounts.getOrDefault(key, 0);
                if (remaining > 0) slots.add(new SchedulePlanner.Slot(date, inspectorId, remaining));
            }
        }
        List<SchedulePlanner.Candidate> candidates = new ArrayList<>();
        for (Map<String, Object> canteen : admin.canteens()) {
            if (!Boolean.TRUE.equals(canteen.get("active"))) continue;
            long canteenId = ((Number) canteen.get("id")).longValue();
            LocalDateTime last = db.lastInspection(canteenId);
            int days = last == null ? 90 : (int) Math.max(0, ChronoUnit.DAYS.between(last.toLocalDate(), start));
            if (last != null && days < 7) continue;
            Map<String, Object> scoreRow = risk.refresh(canteenId);
            double riskScore = scoreRow.get("score") instanceof Number number ? number.doubleValue() : 0;
            int high = db.openHighIssues(canteenId);
            double priority = 0.6 * riskScore + 0.3 * Math.min(days, 90) / 90.0 * 100 + 0.1 * Math.min(high, 3) / 3.0 * 100;
            priority = Math.round(priority * 100.0) / 100.0;
            String reason = "风险分 " + riskScore + "；距上次检查 " + days + " 天；未关闭严重问题 " + high + " 项";
            candidates.add(new SchedulePlanner.Candidate(canteenId, priority, days, reason));
        }
        Set<Long> scheduled = new HashSet<>(db.scheduledCanteens(start, end));
        List<SchedulePlanner.Assignment> plan = SchedulePlanner.plan(candidates, slots, scheduled);
        Set<Long> plannedIds = new HashSet<>();
        for (SchedulePlanner.Assignment assignment : plan) plannedIds.add(assignment.candidate().canteenId());
        List<Map<String, Object>> unassigned = candidates.stream()
                .filter(candidate -> !scheduled.contains(candidate.canteenId()) && !plannedIds.contains(candidate.canteenId()))
                .map(candidate -> Map.<String, Object>of("canteenId", candidate.canteenId(),
                        "priority", candidate.priority(), "reason", "监管人员可用容量不足"))
                .toList();
        for (SchedulePlanner.Assignment assignment : plan) {
            Map<String, Object> value = new HashMap<>();
            value.put("planDate", assignment.slot().date());
            value.put("canteenId", assignment.candidate().canteenId());
            value.put("inspectorId", assignment.slot().inspectorId());
            value.put("priorityScore", assignment.candidate().priority());
            value.put("reason", assignment.candidate().reason());
            db.addSuggestion(value);
        }
        audit.log("GENERATE_SCHEDULE", "schedule_suggestion", null, "week=" + start + ",assigned=" + plan.size());
        return Map.of("suggestions", db.suggestions(start, end),
                "unassignedCount", unassigned.size(), "unassigned", unassigned,
                "message", plan.isEmpty() ? "没有可安排的检查任务或可用监管人员" : "建议已生成，须人工确认");
    }

    @Transactional
    public Map<String, Object> adjust(long id, LocalDate date, long inspectorId) {
        current.requireRole("INSPECTOR");
        Map<String, Object> suggestion = db.suggestion(id);
        if (suggestion == null) throw new ApiException(HttpStatus.NOT_FOUND, "排程建议不存在");
        if (!"SUGGESTED".equals(suggestion.get("status"))) throw new ApiException(HttpStatus.BAD_REQUEST, "只能调整未确认的建议");
        LocalDate original = asDate(suggestion.get("plan_date"));
        LocalDate weekStart = original.minusDays(original.getDayOfWeek().getValue() - 1);
        if (date.isBefore(weekStart) || date.isAfter(weekStart.plusDays(4))) throw new ApiException(HttpStatus.BAD_REQUEST, "只能安排同一工作周内的日期");
        ensureSlot(date, inspectorId);
        db.adjustSuggestion(Map.of("id", id, "planDate", date, "inspectorId", inspectorId));
        audit.log("ADJUST_SCHEDULE", "schedule_suggestion", id, "date=" + date + ",inspector=" + inspectorId);
        return db.suggestion(id);
    }

    private void ensureSlot(LocalDate date, long inspectorId) {
        if (!users.roles(inspectorId).contains("INSPECTOR")) throw new ApiException(HttpStatus.BAD_REQUEST, "账号不是监管人员");
        Map<String, Object> availability = db.availability(date, date).stream()
                .filter(row -> ((Number) row.get("inspector_id")).longValue() == inspectorId).findFirst().orElse(null);
        if (availability != null && !Boolean.TRUE.equals(availability.get("available"))) throw new ApiException(HttpStatus.BAD_REQUEST, "监管人员当天不可用");
        int max = availability == null ? 2 : ((Number) availability.get("max_tasks")).intValue();
        int existing = db.existingTasks(date, date).stream()
                .filter(row -> ((Number) row.get("inspector_id")).longValue() == inspectorId)
                .mapToInt(row -> ((Number) row.get("task_count")).intValue()).sum();
        if (existing >= max) throw new ApiException(HttpStatus.BAD_REQUEST, "监管人员当天任务已满");
    }

    @Transactional
    public Map<String, Object> confirm(long id) {
        current.requireRole("INSPECTOR");
        Map<String, Object> suggestion = db.suggestion(id);
        if (suggestion == null) throw new ApiException(HttpStatus.NOT_FOUND, "排程建议不存在");
        if (!"SUGGESTED".equals(suggestion.get("status"))) throw new ApiException(HttpStatus.BAD_REQUEST, "建议已经处理");
        LocalDate date = asDate(suggestion.get("plan_date"));
        long inspectorId = ((Number) suggestion.get("inspector_id")).longValue();
        ensureSlot(date, inspectorId);
        long canteenId = ((Number) suggestion.get("canteen_id")).longValue();
        if (db.scheduledCanteens(date.minusDays(6), date.plusDays(6)).contains(canteenId)) {
            throw new ApiException(HttpStatus.CONFLICT, "该食堂附近日期已有检查任务");
        }
        Map<String, Object> task = new HashMap<>();
        task.put("canteenId", canteenId);
        task.put("inspectorId", inspectorId);
        Map<String, Object> template = admin.templates().stream()
                .filter(row -> Boolean.TRUE.equals(row.get("active")))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "没有可用的检查模板"));
        task.put("templateId", template.get("id"));
        task.put("scheduledAt", date.atTime(LocalTime.of(9, 0)));
        task.put("note", "由风险驱动检查排程确认生成：" + suggestion.get("reason"));
        task.put("createdBy", current.id());
        supervision.addInspection(task);
        long inspectionId = ((Number) task.get("id")).longValue();
        if (db.confirmSuggestion(id, inspectionId) == 0) throw new ApiException(HttpStatus.CONFLICT, "建议状态已变化，请刷新");
        audit.log("CONFIRM_SCHEDULE", "schedule_suggestion", id, "inspection=" + inspectionId);
        return db.suggestion(id);
    }

    public Map<String, Object> dismiss(long id) {
        current.requireRole("INSPECTOR");
        if (db.dismissSuggestion(id) == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "只能忽略未确认的建议");
        audit.log("DISMISS_SCHEDULE", "schedule_suggestion", id, "人工忽略");
        return db.suggestion(id);
    }
}
