package edu.campus.food.supervision;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/daily-checks")
public class DailyController {
    private final SupervisionMapper db;
    private final AdminMapper admin;
    private final CurrentUser current;
    private final AuditService audit;

    public DailyController(SupervisionMapper db, AdminMapper admin, CurrentUser current, AuditService audit) {
        this.db = db;
        this.admin = admin;
        this.current = current;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        return current.visibleCanteens(db.dailyChecks());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER");
        Map<String, Object> item = db.dailyCheck(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "日常管控记录不存在");
        current.requireCanteen(((Number) item.get("canteen_id")).longValue());
        Map<String, Object> result = new HashMap<>(item);
        result.put("results", db.dailyResults(id));
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        long templateId = Input.id(body, "templateId");
        Map<String, Object> template = admin.template(templateId);
        if (template == null || !Boolean.TRUE.equals(template.get("active"))) throw Input.bad("检查模板不存在或未启用");
        List<Map<String, Object>> expected = admin.templateItems(templateId).stream()
                .filter(row -> Boolean.TRUE.equals(row.get("active"))).toList();
        if (expected.isEmpty()) throw Input.bad("检查模板不存在或没有启用的项目");
        Object raw = body.get("results");
        if (!(raw instanceof List<?> items)) throw Input.bad("请填写全部检查项目");
        List<Map<String, Object>> submitted = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> source)) throw Input.bad("检查结果格式不正确");
            Map<String, Object> row = new HashMap<>();
            source.forEach((key, value) -> row.put(String.valueOf(key), value));
            submitted.add(row);
        }
        Set<Long> expectedIds = expected.stream().map(row -> ((Number) row.get("id")).longValue()).collect(Collectors.toSet());
        Set<Long> submittedIds = submitted.stream().map(row -> Input.id(row, "itemId")).collect(Collectors.toSet());
        if (!expectedIds.equals(submittedIds) || submitted.size() != expected.size()) throw Input.bad("必须逐项填写当前模板的全部检查项");
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("templateId", templateId);
        value.put("checkDate", Input.date(body, "checkDate"));
        value.put("checkedBy", current.id());
        value.put("note", Input.optionalText(body, "note", 3000));
        db.addDailyCheck(value);
        long id = ((Number) value.get("id")).longValue();
        for (Map<String, Object> row : submitted) {
            long itemId = Input.id(row, "itemId");
            Map<String, Object> templateItem = expected.stream()
                    .filter(item -> ((Number) item.get("id")).longValue() == itemId).findFirst().orElseThrow();
            Map<String, Object> result = new HashMap<>();
            result.put("dailyCheckId", id);
            result.put("itemId", itemId);
            result.put("itemTitle", templateItem.get("title"));
            result.put("result", Input.oneOf(row, "result", "PASS", "FAIL", "NA"));
            result.put("note", Input.optionalText(row, "note", 500));
            if ("FAIL".equals(result.get("result")) && String.valueOf(result.get("note")).isBlank()) {
                throw Input.bad("异常检查项必须填写说明");
            }
            db.addDailyResult(result);
        }
        audit.log("CREATE_DAILY_CHECK", "daily_check", id, "canteen=" + canteenId);
        return detail(id);
    }
}
