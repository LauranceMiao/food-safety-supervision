package edu.campus.food.catalog;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.common.Input;
import edu.campus.food.file.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogMapper db;
    private final CurrentUser current;
    private final AuditService audit;
    private final FileService files;

    public CatalogController(CatalogMapper db, CurrentUser current, AuditService audit, FileService files) {
        this.db = db;
        this.current = current;
        this.audit = audit;
        this.files = files;
    }

    private void procurementView() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "PURCHASER");
    }

    private void kitchenView() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "CHEF");
    }

    private boolean oversight() {
        return current.has("MANAGER") || current.has("ADMIN") || current.has("PRINCIPAL") || current.has("INSPECTOR");
    }

    private void chefWrite(long canteenId) {
        current.requireRole("CHEF");
        current.requireCanteen(canteenId);
    }

    private boolean supplierActive(Map<String, Object> supplier) {
        return enabled(supplier.get("active"));
    }

    private boolean enabled(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() == 1;
    }

    private Map<String, Object> batch(long id) {
        Map<String, Object> value = db.batch(id);
        if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "食材批次不存在");
        current.requireCanteen(((Number) value.get("canteen_id")).longValue());
        return value;
    }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        procurementView();
        List<Map<String, Object>> rows = db.suppliers();
        return oversight() ? rows : rows.stream().filter(this::supplierActive).toList();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addSupplier(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> value = new HashMap<>();
        value.put("name", Input.text(body, "name", 160));
        value.put("licenseNo", Input.optionalText(body, "licenseNo", 100));
        value.put("licenseExpiresOn", Input.optionalDate(body, "licenseExpiresOn"));
        value.put("contactName", Input.optionalText(body, "contactName", 100));
        value.put("contactPhone", Input.optionalText(body, "contactPhone", 40));
        value.put("certificatePath", Input.optionalText(body, "certificatePath", 500));
        files.requireOwned((String) value.get("certificatePath"), "SUPPLIER_CERT", 0);
        value.put("createdBy", current.id());
        db.addSupplier(value);
        audit.log("CREATE_SUPPLIER", "supplier", ((Number) value.get("id")).longValue(), String.valueOf(value.get("name")));
        return db.supplier(((Number) value.get("id")).longValue());
    }

    @PutMapping("/suppliers/{id}")
    public Map<String, Object> updateSupplier(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> previous = db.supplier(id);
        if (previous == null) throw new ApiException(HttpStatus.NOT_FOUND, "供应商不存在");
        Map<String, Object> value = new HashMap<>();
        value.put("id", id);
        value.put("name", Input.text(body, "name", 160));
        value.put("licenseNo", Input.optionalText(body, "licenseNo", 100));
        value.put("licenseExpiresOn", Input.optionalDate(body, "licenseExpiresOn"));
        value.put("contactName", Input.optionalText(body, "contactName", 100));
        value.put("contactPhone", Input.optionalText(body, "contactPhone", 40));
        value.put("certificatePath", Input.optionalText(body, "certificatePath", 500));
        if (!value.get("certificatePath").equals(previous.get("certificate_path"))) {
            files.requireOwned((String) value.get("certificatePath"), "SUPPLIER_CERT", 0);
        }
        value.put("active", !Boolean.FALSE.equals(body.get("active")));
        db.updateSupplier(value);
        audit.log("UPDATE_SUPPLIER", "supplier", id, String.valueOf(value));
        return db.supplier(id);
    }

    @GetMapping("/ingredients")
    public List<Map<String, Object>> ingredients() {
        procurementView();
        return db.ingredients();
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addIngredient(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> value = new HashMap<>();
        value.put("name", Input.text(body, "name", 120));
        value.put("category", Input.optionalText(body, "category", 80));
        value.put("unit", Input.text(body, "unit", 30));
        db.addIngredient(value);
        audit.log("CREATE_INGREDIENT", "ingredient", ((Number) value.get("id")).longValue(), String.valueOf(value.get("name")));
        return db.ingredient(((Number) value.get("id")).longValue());
    }

    @GetMapping("/batches")
    public List<Map<String, Object>> batches() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "PURCHASER", "CHEF");
        List<Map<String, Object>> rows = current.visibleCanteens(db.batches());
        if (oversight() || current.has("PURCHASER")) return rows;
        return rows.stream().filter(row -> "ACCEPTED".equals(row.get("status"))).toList();
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> plans() {
        procurementView();
        List<Map<String, Object>> rows = current.visibleCanteens(db.plans());
        return oversight() ? rows : rows.stream().filter(row -> row.get("published_at") != null).toList();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addPlan(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        long ingredientId = Input.id(body, "ingredientId");
        Long supplierId = Input.optionalId(body, "supplierId");
        if (db.ingredient(ingredientId) == null) throw Input.bad("食材不存在");
        if (supplierId != null) {
            Map<String, Object> supplier = db.supplier(supplierId);
            if (supplier == null || !supplierActive(supplier)) throw Input.bad("供应商不存在或已停用");
        }
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("ingredientId", ingredientId);
        value.put("supplierId", supplierId);
        value.put("plannedOn", Input.date(body, "plannedOn"));
        value.put("plannedQuantity", Input.positive(body, "plannedQuantity"));
        value.put("createdBy", current.id());
        db.addPlan(value);
        long id = ((Number) value.get("id")).longValue();
        audit.log("CREATE_PURCHASE_PLAN", "purchase_plan", id, "canteen=" + canteenId);
        return db.plan(id);
    }

    @PostMapping("/plans/{id}/publish")
    @Transactional
    public Map<String, Object> publishPlan(@PathVariable long id) {
        current.requireRole("MANAGER");
        Map<String, Object> plan = db.lockPlan(id);
        if (plan == null) throw new ApiException(HttpStatus.NOT_FOUND, "采购需求不存在");
        current.requireCanteen(((Number) plan.get("canteen_id")).longValue());
        if (db.publishPlan(id, current.id()) == 0) throw Input.bad("只有尚未发布、未关闭的采购需求可以发布");
        audit.log("PUBLISH_PURCHASE_PLAN", "purchase_plan", id, "发布采购需求");
        return db.plan(id);
    }

    @PostMapping("/plans/{id}/status")
    @Transactional
    public Map<String, Object> planStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> plan = db.lockPlan(id);
        if (plan == null) throw new ApiException(HttpStatus.NOT_FOUND, "采购计划不存在");
        current.requireCanteen(((Number) plan.get("canteen_id")).longValue());
        String status = Input.oneOf(body, "status", "CLOSED", "CANCELLED");
        if ("CLOSED".equals(status) && plan.get("published_at") == null) throw Input.bad("未发布的采购需求只能取消");
        if ("CANCELLED".equals(status) && db.planBatchCount(id) > 0) {
            throw Input.bad("该需求已有到货批次，请结束需求并完成已有批次验收，不能撤销");
        }
        if (db.setPlanStatus(id, status) == 0) throw Input.bad("只有进行中的计划可以变更状态");
        audit.log("CLOSE_PURCHASE_PLAN", "purchase_plan", id, status);
        return db.plan(id);
    }

    @GetMapping("/batches/{id}")
    public Map<String, Object> batchDetail(@PathVariable long id) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "PURCHASER", "CHEF");
        Map<String, Object> result = new HashMap<>(batch(id));
        if (!oversight() && !current.has("PURCHASER") && !"ACCEPTED".equals(result.get("status"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该批次尚不可领用");
        }
        result.put("acceptances", db.acceptances(id));
        result.put("usages", db.usagesByBatch(id));
        return result;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> addBatch(@RequestBody Map<String, Object> body) {
        current.requireRole("PURCHASER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        long ingredientId = Input.id(body, "ingredientId");
        long supplierId = Input.id(body, "supplierId");
        Map<String, Object> supplier = db.supplier(supplierId);
        if (db.ingredient(ingredientId) == null || supplier == null || !supplierActive(supplier)) {
            throw Input.bad("食材不存在，或供应商不存在、已停用");
        }
        long planId = Input.id(body, "planId");
        Map<String, Object> plan = db.lockPlan(planId);
        if (plan == null || !"OPEN".equals(plan.get("status")) || plan.get("published_at") == null
                || ((Number) plan.get("canteen_id")).longValue() != canteenId
                || ((Number) plan.get("ingredient_id")).longValue() != ingredientId
                || plan.get("supplier_id") instanceof Number plannedSupplier && plannedSupplier.longValue() != supplierId) {
            throw Input.bad("请选择本食堂已发布且未关闭的采购需求，食材和指定供应商须与需求一致");
        }
        LocalDate receivedOn = Input.date(body, "receivedOn");
        LocalDate productionDate = Input.optionalDate(body, "productionDate");
        LocalDate expiresOn = Input.optionalDate(body, "expiresOn");
        if (productionDate != null && productionDate.isAfter(receivedOn)) throw Input.bad("生产日期不能晚于到货日期");
        if (expiresOn != null && productionDate != null && expiresOn.isBefore(productionDate)) throw Input.bad("保质期日期不能早于生产日期");
        Map<String, Object> value = new HashMap<>();
        value.put("batchCode", "pending-" + UUID.randomUUID());
        value.put("canteenId", canteenId);
        value.put("ingredientId", ingredientId);
        value.put("specification", Input.optionalText(body, "specification", 120));
        value.put("supplierId", supplierId);
        value.put("planId", planId);
        value.put("productionDate", productionDate);
        value.put("expiresOn", expiresOn);
        value.put("receivedOn", receivedOn);
        value.put("quantity", Input.positive(body, "quantity"));
        value.put("voucherPath", Input.optionalText(body, "voucherPath", 500));
        files.requireOwned((String) value.get("voucherPath"), "BATCH_VOUCHER", canteenId);
        value.put("createdBy", current.id());
        db.addBatch(value);
        long id = ((Number) value.get("id")).longValue();
        if (db.setBatchCode(id, String.valueOf(id)) != 1) throw new IllegalStateException("批次编号生成失败");
        audit.log("CREATE_BATCH", "purchase_batch", id, "批次编号=" + id);
        return db.batch(id);
    }

    @PostMapping("/batches/{id}/acceptance")
    @Transactional
    public Map<String, Object> accept(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("PURCHASER");
        Map<String, Object> batch = db.lockBatch(id);
        if (batch == null) throw new ApiException(HttpStatus.NOT_FOUND, "食材批次不存在");
        long canteenId = ((Number) batch.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        if (!"PENDING".equals(batch.get("status"))) throw Input.bad("该批次已完成验收");
        boolean legacyUnplanned = false;
        if (batch.get("plan_id") instanceof Number planId) {
            Map<String, Object> plan = db.lockPlan(planId.longValue());
            if (plan == null || !("OPEN".equals(plan.get("status")) || "CLOSED".equals(plan.get("status"))) || plan.get("published_at") == null
                    || ((Number) plan.get("canteen_id")).longValue() != canteenId
                    || ((Number) plan.get("ingredient_id")).longValue() != ((Number) batch.get("ingredient_id")).longValue()
                    || plan.get("supplier_id") instanceof Number supplierId
                       && supplierId.longValue() != ((Number) batch.get("supplier_id")).longValue()) {
                throw Input.bad("关联的采购需求未发布、已撤销或与批次不一致，请联系总管核查");
            }
        } else {
            legacyUnplanned = enabled(batch.get("legacy_unplanned"));
            if (!legacyUnplanned) throw Input.bad("该批次未关联已发布的采购需求，请联系总管核查");
        }
        String result = Input.oneOf(body, "result", "ACCEPTED", "REJECTED");
        Map<String, Object> value = new HashMap<>();
        value.put("batchId", id);
        value.put("result", result);
        value.put("note", Input.optionalText(body, "note", 3000));
        value.put("evidencePath", Input.optionalText(body, "evidencePath", 500));
        files.requireOwned((String) value.get("evidencePath"), "ACCEPTANCE_EVIDENCE", canteenId);
        value.put("checkedBy", current.id());
        db.addAcceptance(value);
        db.setBatchStatus(id, result);
        audit.log("ACCEPT_BATCH", "purchase_batch", id,
                (legacyUnplanned ? "历史无需求批次验收：" : "") + result + " " + value.get("note"));
        return batchDetail(id);
    }

    @PostMapping("/batches/{id}/stop")
    public Map<String, Object> stopBatch(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER", "INSPECTOR");
        Map<String, Object> batch = batch(id);
        if (!"ACCEPTED".equals(batch.get("status"))) throw Input.bad("只有已验收批次可停用");
        String reason = Input.text(body, "reason", 500);
        db.setBatchStatus(id, "STOPPED");
        audit.log("STOP_BATCH", "purchase_batch", id, reason);
        return db.batch(id);
    }

    @GetMapping("/dishes")
    public List<Map<String, Object>> dishes() {
        kitchenView();
        return current.visibleCanteens(db.dishes());
    }

    @PostMapping("/dishes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addDish(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        value.put("name", Input.text(body, "name", 120));
        value.put("description", Input.optionalText(body, "description", 2000));
        db.addDish(value);
        audit.log("CREATE_DISH", "dish", ((Number) value.get("id")).longValue(), String.valueOf(value.get("name")));
        return db.dish(((Number) value.get("id")).longValue());
    }

    @GetMapping("/dishes/{id}/ingredients")
    public List<Map<String, Object>> dishIngredients(@PathVariable long id) {
        kitchenView();
        Map<String, Object> dish = db.dish(id);
        if (dish == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜品不存在");
        current.requireCanteen(((Number) dish.get("canteen_id")).longValue());
        return db.dishIngredients(id);
    }

    @PostMapping("/dishes/{id}/ingredients")
    public List<Map<String, Object>> addDishIngredient(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> dish = db.dish(id);
        if (dish == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜品不存在");
        current.requireCanteen(((Number) dish.get("canteen_id")).longValue());
        long ingredientId = Input.id(body, "ingredientId");
        if (db.ingredient(ingredientId) == null) throw Input.bad("食材不存在");
        db.addDishIngredient(id, ingredientId);
        audit.log("ADD_DISH_INGREDIENT", "dish", id, "ingredient=" + ingredientId);
        return db.dishIngredients(id);
    }

    @GetMapping("/menus")
    public List<Map<String, Object>> menus() {
        kitchenView();
        List<Map<String, Object>> rows = current.visibleCanteens(db.menus());
        if (oversight()) return rows;
        return rows.stream().filter(row -> row.get("published_at") != null).toList();
    }

    @PostMapping("/menus")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addMenu(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        return createMenuEntry(body, canteenId, Input.date(body, "serviceDate"));
    }

    @PostMapping("/menus/daily")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<Map<String, Object>> addDailyMenu(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        LocalDate serviceDate = Input.date(body, "serviceDate");
        if (!(body.get("items") instanceof List<?> items) || items.isEmpty() || items.size() > 30) {
            throw Input.bad("每日食谱需包含1至30道菜品");
        }
        List<Map<String, Object>> created = new java.util.ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> raw)) throw Input.bad("菜品信息格式不正确");
            Map<String, Object> values = new HashMap<>();
            raw.forEach((key, value) -> values.put(String.valueOf(key), value));
            created.add(createMenuEntry(values, canteenId, serviceDate));
        }
        return created;
    }

    @PostMapping("/menus/daily/publish")
    @Transactional
    public Map<String, Object> publishDailyMenu(@RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        long canteenId = Input.id(body, "canteenId");
        current.requireCanteen(canteenId);
        LocalDate serviceDate = Input.date(body, "serviceDate");
        int count = db.publishDaily(canteenId, serviceDate, current.id());
        if (count == 0) throw Input.bad("该日期没有待发布的菜品");
        audit.log("PUBLISH_DAILY_MENU", "canteen", canteenId, "date=" + serviceDate + ",items=" + count);
        return Map.of("canteenId", canteenId, "serviceDate", serviceDate, "publishedCount", count);
    }

    @PutMapping("/menus/{id}")
    @Transactional
    public Map<String, Object> updateDraftMenu(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> previous = db.lockMenu(id);
        if (previous == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        long canteenId = ((Number) previous.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        if (!"PLANNED".equals(previous.get("status")) || previous.get("published_at") != null) {
            throw Input.bad("只有未发布的食谱草稿可以修改");
        }
        LocalDate serviceDate = previous.get("service_date") instanceof java.sql.Date date
                ? date.toLocalDate() : (LocalDate) previous.get("service_date");
        Map<String, Object> value = menuValues(body, canteenId, serviceDate);
        value.put("id", id);
        if (db.menuExists(value) > 0) throw Input.bad("该餐次和档口已安排过这道菜");
        if (db.updateDraftMenu(value) == 0) throw Input.bad("食谱草稿状态已改变，请刷新后重试");
        audit.log("UPDATE_MENU_DRAFT", "menu_entry", id, "before=" + previous + ",after=" + value);
        return db.menu(id);
    }

    private Map<String, Object> createMenuEntry(Map<String, Object> body, long canteenId, LocalDate serviceDate) {
        Map<String, Object> value = menuValues(body, canteenId, serviceDate);
        if (db.menuExists(value) > 0) throw Input.bad("该餐次和档口已安排过这道菜");
        value.put("createdBy", current.id());
        db.addMenu(value);
        audit.log("CREATE_MENU", "menu_entry", ((Number) value.get("id")).longValue(), "dish=" + value.get("dishId"));
        return db.menu(((Number) value.get("id")).longValue());
    }

    private Map<String, Object> menuValues(Map<String, Object> body, long canteenId, LocalDate serviceDate) {
        long dishId = Input.id(body, "dishId");
        Map<String, Object> dish = db.dish(dishId);
        if (dish == null || ((Number) dish.get("canteen_id")).longValue() != canteenId) throw Input.bad("菜品不属于该食堂");
        Map<String, Object> value = new HashMap<>();
        value.put("canteenId", canteenId);
        Long stallId = Input.optionalId(body, "stallId");
        if (stallId != null && !Long.valueOf(canteenId).equals(db.stall(stallId))) throw Input.bad("档口不属于该食堂");
        value.put("stallId", stallId);
        value.put("dishId", dishId);
        value.put("serviceDate", serviceDate);
        value.put("meal", Input.oneOf(body, "meal", "BREAKFAST", "LUNCH", "DINNER"));
        Object rawPortions = body.get("targetPortions");
        if (rawPortions != null && !String.valueOf(rawPortions).isBlank()) {
            try {
                int portions = Integer.parseInt(String.valueOf(rawPortions));
                if (portions <= 0 || portions > 100000) throw new NumberFormatException();
                value.put("targetPortions", portions);
            } catch (NumberFormatException ex) {
                throw Input.bad("预计份数应为1至100000的整数");
            }
        } else value.put("targetPortions", null);
        value.put("cookingNote", Input.optionalText(body, "cookingNote", 500));
        return value;
    }

    @GetMapping("/menus/{id}/usages")
    public List<Map<String, Object>> menuUsages(@PathVariable long id) {
        kitchenView();
        Map<String, Object> menu = db.menu(id);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        current.requireCanteen(((Number) menu.get("canteen_id")).longValue());
        if (!oversight() && menu.get("published_at") == null) throw new ApiException(HttpStatus.NOT_FOUND, "食谱尚未发布");
        return db.usagesByMenu(id);
    }

    @PostMapping("/menus/{id}/usages")
    @Transactional
    public Map<String, Object> addUsage(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("CHEF");
        Map<String, Object> menu = db.lockMenu(id);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        long canteenId = ((Number) menu.get("canteen_id")).longValue();
        chefWrite(canteenId);
        if (!"PLANNED".equals(menu.get("status"))) throw Input.bad("只有计划中的菜品可以登记用料");
        if (menu.get("published_at") == null) throw Input.bad("先发布每日食谱，再登记实际用料");
        long batchId = Input.id(body, "batchId");
        Map<String, Object> batch = db.lockBatch(batchId);
        if (batch == null || ((Number) batch.get("canteen_id")).longValue() != canteenId) throw Input.bad("批次不属于该食堂");
        if (!"ACCEPTED".equals(batch.get("status"))) throw Input.bad("批次尚未验收合格或已停用");
        Object expiresOn = batch.get("expires_on");
        LocalDate expiry = expiresOn instanceof java.sql.Date date ? date.toLocalDate()
                : expiresOn instanceof LocalDate date ? date : null;
        if (expiry != null && expiry.isBefore(LocalDate.now())) {
            throw Input.bad("过期食材批次不能领用");
        }
        long dishId = ((Number) menu.get("dish_id")).longValue();
        long ingredientId = ((Number) batch.get("ingredient_id")).longValue();
        boolean inRecipe = db.dishIngredients(dishId).stream().anyMatch(row -> ((Number) row.get("id")).longValue() == ingredientId);
        if (!inRecipe) throw Input.bad("该食材不在菜品原料清单中");
        BigDecimal quantity = Input.positive(body, "quantity");
        if (db.consumeBatch(batchId, quantity) == 0) throw Input.bad("批次可用数量不足");
        Map<String, Object> value = new HashMap<>();
        value.put("batchId", batchId);
        value.put("menuEntryId", id);
        value.put("quantity", quantity);
        value.put("recordedBy", current.id());
        db.addUsage(value);
        audit.log("USE_BATCH", "batch_usage", ((Number) value.get("id")).longValue(), "batch=" + batchId + ",menu=" + id + ",quantity=" + quantity);
        return value;
    }

    @PostMapping("/menus/{id}/serve")
    @Transactional
    public Map<String, Object> serve(@PathVariable long id) {
        current.requireRole("CHEF");
        Map<String, Object> menu = db.lockMenu(id);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        chefWrite(((Number) menu.get("canteen_id")).longValue());
        if (!"PLANNED".equals(menu.get("status"))) throw Input.bad("只有计划中的菜品可以确认供餐");
        if (menu.get("published_at") == null) throw Input.bad("食谱尚未发布");
        if (menu.get("prepared_at") == null) throw Input.bad("请厨师先确认菜品已制作");
        if (db.usagesByMenu(id).isEmpty()) throw Input.bad("先登记实际使用的食材批次");
        db.setMenuStatus(id, "SERVED");
        audit.log("SERVE_MENU", "menu_entry", id, "确认供餐");
        return db.menu(id);
    }

    @PostMapping("/menus/{id}/prepare")
    @Transactional
    public Map<String, Object> prepareMenu(@PathVariable long id) {
        current.requireRole("CHEF");
        Map<String, Object> menu = db.lockMenu(id);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        long canteenId = ((Number) menu.get("canteen_id")).longValue();
        current.requireCanteen(canteenId);
        if (db.markPrepared(id, current.id()) == 0) throw Input.bad("只有已发布、未完成制作的计划菜品可以确认制作");
        audit.log("PREPARE_MENU", "menu_entry", id, "确认制作完成");
        return db.menu(id);
    }

    @PostMapping("/menus/{id}/cancel")
    @Transactional
    public Map<String, Object> cancelMenu(@PathVariable long id, @RequestBody Map<String, Object> body) {
        current.requireRole("MANAGER");
        Map<String, Object> menu = db.lockMenu(id);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "菜单记录不存在");
        current.requireCanteen(((Number) menu.get("canteen_id")).longValue());
        if (!db.usagesByMenu(id).isEmpty()) throw Input.bad("已登记实际用料的菜单不能直接撤销，请先联系监管人员核查");
        String reason = Input.text(body, "reason", 500);
        if (db.cancelMenu(id, reason) == 0) throw Input.bad("只有计划中的菜单可以撤销");
        audit.log("CANCEL_MENU", "menu_entry", id, reason);
        return db.menu(id);
    }
}
