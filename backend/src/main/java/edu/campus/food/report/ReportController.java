package edu.campus.food.report;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportMapper db;
    private final AdminMapper admin;
    private final CurrentUser current;
    private final AuditService audit;

    public ReportController(ReportMapper db, AdminMapper admin, CurrentUser current, AuditService audit) {
        this.db = db;
        this.admin = admin;
        this.current = current;
        this.audit = audit;
    }

    private List<Map<String, Object>> metrics(LocalDate from, LocalDate to, Long canteenId) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(365))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择不超过366天的有效统计区间");
        }
        if (canteenId != null) current.requireCanteen(canteenId);
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate toExclusive = to.plusDays(1);
        for (Map<String, Object> canteen : admin.canteens()) {
            long id = ((Number) canteen.get("id")).longValue();
            if (canteenId != null && id != canteenId) continue;
            if (!(current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR"))
                    && !current.canteens().contains(id)) continue;
            int issues = db.issues(id, from, toExclusive);
            int closed = db.closedIssues(id, from, toExclusive);
            int feedback = db.feedbacks(id, from, toExclusive);
            int handled = db.handledFeedbacks(id, from, toExclusive);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canteenId", id);
            row.put("canteenName", canteen.get("name"));
            row.put("inspectionCount", db.completedInspections(id, from, toExclusive));
            row.put("issueCount", issues);
            row.put("closedIssueCount", closed);
            row.put("rectificationRate", issues == 0 ? null : Math.round(closed * 10000.0 / issues) / 100.0);
            row.put("overdueIssueCount", db.overdueIssues(id, from, toExclusive));
            row.put("rejectedBatchCount", db.rejectedBatches(id, from, toExclusive));
            row.put("feedbackCount", feedback);
            row.put("handledFeedbackCount", handled);
            row.put("feedbackHandlingRate", feedback == 0 ? null : Math.round(handled * 10000.0 / feedback) / 100.0);
            result.add(row);
        }
        if (canteenId != null && result.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "食堂不存在");
        return result;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metricsResponse(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                @RequestParam(required = false) Long canteenId) {
        return Map.of("from", from, "to", to, "canteens", metrics(from, to, canteenId),
                "definitions", Map.of("rectificationRate", "统计区间内创建的问题单中，当前已关闭的比例",
                        "feedbackHandlingRate", "统计区间内创建的反馈中，当前已回复或关闭的比例"));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                         @RequestParam(required = false) Long canteenId) {
        List<Map<String, Object>> rows = metrics(from, to, canteenId);
        StringBuilder csv = new StringBuilder("\uFEFF食堂,已完成检查,问题数,已关闭问题,整改完成率(%),逾期问题,拒收批次,反馈数,已处理反馈,反馈处理率(%)\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(quote(row.get("canteenName")));
            for (String key : List.of("inspectionCount", "issueCount", "closedIssueCount", "rectificationRate",
                    "overdueIssueCount", "rejectedBatchCount", "feedbackCount", "handledFeedbackCount", "feedbackHandlingRate")) {
                csv.append(',').append(quote(row.get(key)));
            }
            csv.append("\r\n");
        }
        audit.log("EXPORT_REPORT", "report", null, "from=" + from + ",to=" + to + ",canteen=" + canteenId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=food-safety-report-" + from + "-" + to + ".csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String quote(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.startsWith("=") || text.startsWith("+") || text.startsWith("-") || text.startsWith("@")) text = "'" + text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
