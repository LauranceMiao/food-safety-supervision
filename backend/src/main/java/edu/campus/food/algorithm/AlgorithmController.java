package edu.campus.food.algorithm;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.Input;
import edu.campus.food.supervision.SupervisionMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlgorithmController {
    private final RiskService risk;
    private final TraceService trace;
    private final ScheduleService schedule;
    private final AdminMapper admin;
    private final SupervisionMapper supervision;
    private final CurrentUser current;

    public AlgorithmController(RiskService risk, TraceService trace, ScheduleService schedule,
                               AdminMapper admin, SupervisionMapper supervision, CurrentUser current) {
        this.risk = risk;
        this.trace = trace;
        this.schedule = schedule;
        this.admin = admin;
        this.supervision = supervision;
        this.current = current;
    }

    @GetMapping("/risk")
    public List<Map<String, Object>> risks() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> canteen : admin.canteens()) {
            long id = ((Number) canteen.get("id")).longValue();
            if (!(current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR"))
                    && !current.canteens().contains(id)) continue;
            Map<String, Object> row = new HashMap<>(risk.refresh(id));
            row.put("canteen_name", canteen.get("name"));
            result.add(row);
        }
        return result;
    }

    @GetMapping("/risk/{canteenId}/history")
    public List<Map<String, Object>> riskHistory(@PathVariable long canteenId) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        current.requireCanteen(canteenId);
        return risk.history(canteenId);
    }

    @GetMapping("/admin/risk-config")
    public Map<String, Object> riskConfig() {
        current.requireRole("ADMIN");
        return risk.config();
    }

    @PutMapping("/admin/risk-config")
    public Map<String, Object> changeRiskConfig(@RequestBody Map<String, Object> body) {
        return risk.changeConfig(body);
    }

    @GetMapping("/trace/batches/{id}")
    public Map<String, Object> batchTrace(@PathVariable long id) {
        return trace.batchTrace(id);
    }

    @GetMapping("/trace/menus/{id}")
    public Map<String, Object> menuTrace(@PathVariable long id) {
        return trace.menuTrace(id);
    }

    @GetMapping("/schedule")
    public List<Map<String, Object>> schedule(@RequestParam LocalDate start) {
        return schedule.suggestions(start);
    }

    @PostMapping("/schedule/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        return schedule.generate(Input.date(body, "start"));
    }

    @PostMapping("/schedule/{id}/adjust")
    public Map<String, Object> adjust(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return schedule.adjust(id, Input.date(body, "date"), Input.id(body, "inspectorId"));
    }

    @PostMapping("/schedule/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id) {
        return schedule.confirm(id);
    }

    @PostMapping("/schedule/{id}/dismiss")
    public Map<String, Object> dismiss(@PathVariable long id) {
        return schedule.dismiss(id);
    }

    @GetMapping("/schedule/availability")
    public List<Map<String, Object>> availability(@RequestParam LocalDate start) {
        return schedule.availability(start);
    }

    @PutMapping("/schedule/availability")
    public Map<String, Boolean> setAvailability(@RequestBody Map<String, Object> body) {
        Object max = body.get("maxTasks");
        int maxTasks = max instanceof Number number ? number.intValue() : 2;
        schedule.setAvailability(Input.id(body, "inspectorId"), Input.date(body, "date"),
                !Boolean.FALSE.equals(body.get("available")), maxTasks);
        return Map.of("ok", true);
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> summary() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        List<Map<String, Object>> issues = current.visibleCanteens(supervision.issues());
        List<Map<String, Object>> inspections = current.visibleCanteens(supervision.inspections());
        List<Map<String, Object>> feedback = current.has("ADMIN") || current.has("INSPECTOR")
                || current.has("PRINCIPAL") ? supervision.feedbacks() : List.of();
        long closed = issues.stream().filter(row -> "CLOSED".equals(row.get("status"))).count();
        long overdue = issues.stream().filter(row -> Boolean.TRUE.equals(row.get("overdue"))).count();
        long completed = inspections.stream().filter(row -> "COMPLETED".equals(row.get("status"))).count();
        return Map.of("issueCount", issues.size(), "closedIssueCount", closed, "overdueIssueCount", overdue,
                "completedInspectionCount", completed, "feedbackCount", feedback.size(),
                "issueSummary", current.visibleCanteens(supervision.issueSummary()),
                "inspectionSummary", current.visibleCanteens(supervision.inspectionSummary()),
                "feedbackSummary", current.has("ADMIN") || current.has("INSPECTOR") || current.has("PRINCIPAL")
                        ? supervision.feedbackSummary() : List.of());
    }
}
