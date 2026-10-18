package edu.campus.food.algorithm;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RiskService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> KEYS = List.of("issues", "overdue", "repeat", "certificate", "rejection", "feedback");
    private final AlgorithmMapper db;
    private final AdminMapper admin;
    private final JsonMapper json;
    private final CurrentUser current;
    private final AuditService audit;

    public RiskService(AlgorithmMapper db, AdminMapper admin, JsonMapper json, CurrentUser current, AuditService audit) {
        this.db = db;
        this.admin = admin;
        this.json = json;
        this.current = current;
        this.audit = audit;
    }

    public Map<String, Object> config() {
        Map<String, Object> row = db.activeConfig();
        if (row == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "风险评分参数尚未初始化");
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> numbers(Object source) {
        Map<String, Object> raw;
        try {
            raw = json.readValue(String.valueOf(source), Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("风险评分参数格式不正确", ex);
        }
        if (raw == null) throw new ApiException(HttpStatus.BAD_REQUEST, "评分参数不能为空");
        Map<String, Double> result = new HashMap<>();
        raw.forEach((key, value) -> {
            if (!(value instanceof Number number)) throw new ApiException(HttpStatus.BAD_REQUEST, "评分参数必须为数字");
            result.put(key, number.doubleValue());
        });
        return result;
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("JSON 编码失败", ex); }
    }

    private boolean sameJson(Object left, Object right) {
        try {
            return java.util.Objects.equals(json.readValue(String.valueOf(left), Map.class),
                    json.readValue(String.valueOf(right), Map.class));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean sameScore(Object left, Object right) {
        if (left == null || right == null) return left == null && right == null;
        return new BigDecimal(String.valueOf(left)).compareTo(new BigDecimal(String.valueOf(right))) == 0;
    }

    @Transactional
    public Map<String, Object> changeConfig(Map<String, Object> body) {
        current.requireRole("ADMIN");
        if (!(body.get("weights") instanceof Map) || !(body.get("thresholds") instanceof Map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请提供指标权重和阈值对象");
        }
        Map<String, Double> weights = numbers(encode(body.get("weights")));
        Map<String, Double> thresholds = numbers(encode(body.get("thresholds")));
        if (!weights.keySet().equals(new java.util.HashSet<>(KEYS))) throw new ApiException(HttpStatus.BAD_REQUEST, "评分指标必须完整且不能增加未知指标");
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.000001 || weights.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "权重必须非负且总和为 1");
        }
        if (!thresholds.keySet().equals(java.util.Set.of("medium", "high"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "阈值只能包含 medium 和 high");
        }
        double medium = thresholds.getOrDefault("medium", -1.0);
        double high = thresholds.getOrDefault("high", -1.0);
        if (!(medium > 0 && high > medium && high <= 100) || !Double.isFinite(medium) || !Double.isFinite(high)) throw new ApiException(HttpStatus.BAD_REQUEST, "风险阈值不正确");
        int version = db.nextConfigVersion();
        db.deactivateConfigs();
        Map<String, Object> value = new HashMap<>();
        value.put("version", version);
        value.put("weights", encode(weights));
        value.put("thresholds", encode(thresholds));
        value.put("createdBy", current.id());
        db.addConfig(value);
        audit.log("CHANGE_RISK_CONFIG", "risk_config", (long) version, value.get("weights") + " " + value.get("thresholds"));
        return config();
    }

    private static double bounded(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp stamp) return stamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    @Transactional
    public Map<String, Object> refresh(long canteenId) {
        if (admin.canteen(canteenId) == null) throw new ApiException(HttpStatus.NOT_FOUND, "食堂不存在");
        Map<String, Object> config = config();
        Map<String, Double> weights = numbers(config.get("weights_json"));
        Map<String, Double> thresholds = numbers(config.get("thresholds_json"));
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(89);
        List<Map<String, Object>> issues = db.recentIssues(canteenId, from);
        int dailyCount = db.dailyCount(canteenId, from);
        int inspectionCount = db.inspectionCount(canteenId, from);
        int batchCount = db.batchCount(canteenId, from);
        int expired = db.expiredSuppliers(canteenId);
        int rejected = db.rejectedBatches(canteenId, from);
        int feedback = db.unresolvedFeedback(canteenId, from);
        boolean enoughData = dailyCount + inspectionCount + batchCount + issues.size() + feedback > 0;

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("dailyChecks", dailyCount);
        breakdown.put("inspections", inspectionCount);
        breakdown.put("batches", batchCount);
        breakdown.put("issueCount", issues.size());
        breakdown.put("expiredSupplierCount", expired);
        breakdown.put("rejectedBatchCount", rejected);
        breakdown.put("unresolvedFeedbackCount", feedback);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("canteenId", canteenId);
        snapshot.put("configVersion", ((Number) config.get("version")).intValue());
        snapshot.put("windowStart", from);
        snapshot.put("windowEnd", today);
        if (!enoughData) {
            snapshot.put("score", null);
            snapshot.put("level", "INSUFFICIENT");
            breakdown.put("message", "近90天没有足够业务记录，不能判为零风险");
        } else {
            double issueMass = 0;
            int overdue = 0;
            Map<String, Integer> categoryCounts = new HashMap<>();
            for (Map<String, Object> issue : issues) {
                String severity = String.valueOf(issue.get("severity"));
                double severityValue = switch (severity) { case "HIGH" -> 3; case "MEDIUM" -> 2; default -> 1; };
                LocalDateTime created = dateTime(issue.get("created_at"));
                long days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(created.toLocalDate(), today));
                double decay = "CLOSED".equals(issue.get("status")) ? Math.pow(0.5, days / 30.0) : 1.0;
                issueMass += severityValue * decay;
                if (!"CLOSED".equals(issue.get("status")) && dateTime(issue.get("due_at")).isBefore(LocalDateTime.now(ZONE))) overdue++;
                categoryCounts.merge(String.valueOf(issue.get("category")), 1, Integer::sum);
            }
            int repeatedCategories = (int) categoryCounts.values().stream().filter(count -> count > 1).count();
            Map<String, Double> components = new LinkedHashMap<>();
            components.put("issues", bounded(issueMass / 10));
            components.put("overdue", bounded(overdue / 3.0));
            components.put("repeat", bounded(repeatedCategories / 2.0));
            components.put("certificate", bounded(expired / 2.0));
            components.put("rejection", bounded(rejected / 3.0));
            components.put("feedback", bounded(feedback / 5.0) * 0.5);
            double score = 0;
            Map<String, Double> contributions = new LinkedHashMap<>();
            for (String key : KEYS) {
                double contribution = 100 * weights.get(key) * components.get(key);
                contributions.put(key, Math.round(contribution * 100.0) / 100.0);
                score += contribution;
            }
            score = Math.round(score * 100.0) / 100.0;
            snapshot.put("score", score);
            snapshot.put("level", score >= thresholds.get("high") ? "HIGH" : score >= thresholds.get("medium") ? "MEDIUM" : "LOW");
            breakdown.put("overdueCount", overdue);
            breakdown.put("repeatedCategoryCount", repeatedCategories);
            breakdown.put("components", components);
            breakdown.put("contributions", contributions);
            breakdown.put("weights", weights);
        }
        snapshot.put("breakdown", encode(breakdown));
        Map<String, Object> latest = db.latestRisk(canteenId);
        if (latest != null && String.valueOf(latest.get("level")).equals(snapshot.get("level"))
                && sameScore(latest.get("score"), snapshot.get("score"))
                && ((Number) latest.get("config_version")).intValue() == ((Number) snapshot.get("configVersion")).intValue()
                && String.valueOf(latest.get("window_end")).equals(String.valueOf(today))
                && sameJson(latest.get("breakdown_json"), snapshot.get("breakdown"))) {
            return latest;
        }
        db.saveRisk(snapshot);
        return db.latestRisk(canteenId);
    }

    @Scheduled(cron = "0 5 1 * * *", zone = "Asia/Shanghai")
    public void nightlyRefresh() {
        for (Map<String, Object> canteen : admin.canteens()) {
            refresh(((Number) canteen.get("id")).longValue());
        }
    }

    public List<Map<String, Object>> history(long canteenId) {
        return db.riskHistory(canteenId);
    }
}
