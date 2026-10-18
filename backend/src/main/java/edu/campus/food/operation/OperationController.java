package edu.campus.food.operation;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationMapper db;
    private final CurrentUser current;
    private final AuditService audit;
    private final JsonMapper json;

    public OperationController(OperationMapper db, CurrentUser current, AuditService audit, JsonMapper json) {
        this.db = db;
        this.current = current;
        this.audit = audit;
        this.json = json;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "CHEF", "CLEANER");
        List<Map<String, Object>> rows = current.visibleCanteens(db.all());
        if (current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR") || current.has("MANAGER")) return rows;
        return rows.stream().filter(row -> allowedCategory(String.valueOf(row.get("category")))).toList();
    }

    private boolean allowedCategory(String category) {
        return "SAMPLE".equals(category) ? current.has("CHEF")
                : ("CLEANING".equals(category) || "DISINFECTION".equals(category)) && current.has("CLEANER");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        current.requireRole("CHEF", "CLEANER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        String category = Input.oneOf(body, "category", "SAMPLE", "DISINFECTION", "CLEANING");
        if (!allowedCategory(category)) {
            throw new edu.campus.food.common.ApiException(HttpStatus.FORBIDDEN, "厨师负责食品留样，保洁负责清洁和消毒，请使用对应岗位账号");
        }
        String meal = Input.oneOf(body, "meal", "BREAKFAST", "LUNCH", "DINNER", "NONE");
        if (!(body.get("details") instanceof Map<?, ?> raw)) throw Input.bad("请填写业务表单内容");
        Map<String, Object> source = new HashMap<>();
        raw.forEach((key, value) -> source.put(String.valueOf(key), value));
        Map<String, Object> details = new HashMap<>();
        switch (category) {
            case "SAMPLE" -> {
                details.put("dishName", Input.text(source, "dishName", 120));
                details.put("quantityGrams", Input.positive(source, "quantityGrams"));
                details.put("storageLocation", Input.text(source, "storageLocation", 160));
                details.put("sampledAt", Input.dateTime(source, "sampledAt"));
            }
            case "DISINFECTION" -> {
                details.put("equipment", Input.text(source, "equipment", 160));
                details.put("method", Input.text(source, "method", 160));
                details.put("durationMinutes", Input.positive(source, "durationMinutes"));
                details.put("completedAt", Input.dateTime(source, "completedAt"));
            }
            case "CLEANING" -> {
                details.put("area", Input.text(source, "area", 160));
                details.put("method", Input.text(source, "method", 160));
                details.put("completedAt", Input.dateTime(source, "completedAt"));
            }
            default -> throw Input.bad("表单类型不正确");
        }
        details.put("note", Input.optionalText(source, "note", 1000));
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("category", category);
        value.put("operationDate", Input.date(body, "operationDate"));
        value.put("meal", meal);
        try { value.put("detailsJson", json.writeValueAsString(details)); }
        catch (Exception ex) { throw new IllegalStateException("表单内容无法保存", ex); }
        value.put("recordedBy", current.id());
        db.add(value);
        long id = ((Number) value.get("id")).longValue();
        audit.log("CREATE_OPERATION_LOG", "operation_log", id, category + ",canteen=" + canteenId);
        return db.one(id);
    }
}
