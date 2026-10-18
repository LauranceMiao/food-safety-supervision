package edu.campus.food.catalog;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.operation.OperationController;
import edu.campus.food.operation.OperationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requires a migrated local MySQL database and the existing work accounts.
 * Run explicitly with RUN_DB_WORKFLOW_TESTS=true. All business writes roll back;
 * this test never uploads files and never creates or changes work accounts.
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_WORKFLOW_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
@Transactional
@Rollback
class DutiesWorkflowIntegrationTest {
    @Autowired private CatalogController catalog;
    @Autowired private CatalogMapper db;
    @Autowired private OperationController operations;
    @Autowired private OperationMapper operationDb;
    @Autowired private AdminMapper admin;
    @Autowired private AuthService auth;
    @Autowired private UserMapper users;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;

    private final Map<String, Long> createdIds = new HashMap<>();

    @BeforeEach
    void attachRequestForAudit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private long login(String username, String requiredRole) {
        Map<String, Object> account = users.byUsername(username);
        assertNotNull(account, "请先创建既有工作账号：" + username);
        long id = ((Number) account.get("id")).longValue();
        assertTrue(users.roles(id).contains(requiredRole), username + " 必须已分配 " + requiredRole);
        var details = auth.loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "", details.getAuthorities()));
        return id;
    }

    private long canteen(String name) {
        return admin.canteens().stream().filter(row -> name.equals(row.get("name")))
                .map(row -> ((Number) row.get("id")).longValue()).findFirst()
                .orElseThrow(() -> new AssertionError("缺少食堂：" + name));
    }

    private long remember(String table, Map<String, Object> value) {
        long id = ((Number) value.get("id")).longValue();
        createdIds.put(table, id);
        return id;
    }

    private boolean includes(List<Map<String, Object>> rows, long id) {
        return rows.stream().anyMatch(row -> ((Number) row.get("id")).longValue() == id);
    }

    private void forbidden(Runnable action) {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, action::run).status());
    }

    @Test
    void publishedDemandToAcceptanceAndRecipeToServingFollowSeparatedDuties() throws Exception {
        long firstCanteen = canteen("第一食堂");
        long secondCanteen = canteen("第二食堂");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String marker = "事务回滚验证-" + UUID.randomUUID().toString().substring(0, 8);

        long managerId = login("zongguan", "MANAGER");
        assertTrue(users.scopes(managerId).contains(firstCanteen));
        long ingredientId = remember("ingredient", catalog.addIngredient(Map.of(
                "name", marker + "食材", "category", "肉类", "unit", "千克")));
        long supplierId = remember("supplier", catalog.addSupplier(Map.of("name", marker + "供应商")));
        long dishId = remember("dish", catalog.addDish(Map.of(
                "canteenId", firstCanteen, "name", marker + "菜品", "description", "仅事务内测试")));
        catalog.addDishIngredient(dishId, Map.of("ingredientId", ingredientId));

        long planId = remember("purchase_plan", catalog.addPlan(Map.of(
                "canteenId", firstCanteen, "ingredientId", ingredientId,
                "plannedOn", today.toString(), "plannedQuantity", 10)));
        assertNull(db.plan(planId).get("supplier_id"));
        assertNull(db.plan(planId).get("published_at"));
        long menuId = remember("menu_entry", catalog.addDailyMenu(Map.of(
                "canteenId", firstCanteen, "serviceDate", today.toString(), "items", List.of(
                        Map.of("dishId", dishId, "meal", "LUNCH", "targetPortions", 20,
                                "cookingNote", "少盐，完全熟制")))).get(0));
        assertTrue(includes(catalog.menus(), menuId), "总管应看见刚添加的食谱草稿");
        String serializedDate = json.readTree(json.writeValueAsString(db.menu(menuId)))
                .get("service_date").asText();
        assertEquals(today.toString(), serializedDate, "业务日期必须输出 yyyy-MM-dd，不能带UTC偏移或时间");
        forbidden(() -> catalog.prepareMenu(menuId));

        long chefId = login("chushi", "CHEF");
        assertTrue(users.scopes(chefId).contains(firstCanteen));
        assertFalse(includes(catalog.menus(), menuId), "厨师不应看见未发布食谱");
        forbidden(catalog::plans);
        forbidden(() -> catalog.publishPlan(planId));

        long purchaserId = login("caigou", "PURCHASER");
        assertTrue(users.scopes(purchaserId).contains(firstCanteen));
        assertFalse(includes(catalog.plans(), planId), "采购员不应看见未发布采购需求");
        forbidden(catalog::menus);
        forbidden(() -> catalog.addPlan(Map.of()));

        login("zongguan", "MANAGER");
        catalog.publishPlan(planId);
        assertNotNull(db.plan(planId).get("published_at"));
        assertEquals(managerId, ((Number) db.plan(planId).get("published_by")).longValue());
        forbidden(() -> catalog.addBatch(Map.of()));

        login("caigou", "PURCHASER");
        assertTrue(includes(catalog.plans(), planId));
        Map<String, Object> arrival = Map.of("canteenId", firstCanteen, "ingredientId", ingredientId,
                "supplierId", supplierId, "planId", planId, "receivedOn", today.toString(),
                "productionDate", today.toString(), "expiresOn", today.plusDays(7).toString(), "quantity", 10);
        long batchId = remember("purchase_batch", catalog.addBatch(arrival));
        assertEquals(String.valueOf(batchId), db.batch(batchId).get("batch_code"));
        assertEquals("PENDING", db.batch(batchId).get("status"));
        assertEquals(purchaserId, ((Number) db.batch(batchId).get("created_by")).longValue());

        login("zongguan", "MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> catalog.planStatus(planId, Map.of("status", "CANCELLED"))).status());
        catalog.planStatus(planId, Map.of("status", "CLOSED"));
        assertEquals("CLOSED", db.plan(planId).get("status"));
        forbidden(() -> catalog.accept(batchId, Map.of("result", "ACCEPTED")));

        login("caigou", "PURCHASER");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> catalog.addBatch(arrival)).status(), "结束需求后不得继续登记新到货");
        catalog.accept(batchId, Map.of("result", "ACCEPTED", "note", "外观、数量与证明核验合格"));
        assertEquals("ACCEPTED", db.batch(batchId).get("status"), "结束需求后仍须能完成已到货批次验收");
        assertEquals(purchaserId, ((Number) db.acceptances(batchId).get(0).get("checked_by")).longValue());
        forbidden(() -> catalog.prepareMenu(menuId));

        login("zongguan", "MANAGER");
        catalog.publishDailyMenu(Map.of("canteenId", firstCanteen, "serviceDate", today.toString()));
        assertNotNull(db.menu(menuId).get("published_at"));
        forbidden(() -> catalog.addUsage(menuId, Map.of("batchId", batchId, "quantity", 3)));
        forbidden(() -> catalog.serve(menuId));

        login("chushi", "CHEF");
        assertTrue(includes(catalog.menus(), menuId));
        assertTrue(includes(catalog.batches(), batchId));
        long usageId = remember("batch_usage", catalog.addUsage(menuId, Map.of("batchId", batchId, "quantity", 3)));
        assertTrue(usageId > 0);
        assertEquals(0, new BigDecimal("7").compareTo((BigDecimal) db.batch(batchId).get("available_quantity")));
        catalog.prepareMenu(menuId);
        assertEquals(chefId, ((Number) db.menu(menuId).get("prepared_by")).longValue());
        catalog.serve(menuId);
        assertEquals("SERVED", db.menu(menuId).get("status"));
        assertEquals(chefId, ((Number) db.usagesByMenu(menuId).get(0).get("recorded_by")).longValue());
        forbidden(() -> operations.add(Map.of("canteenId", firstCanteen, "category", "CLEANING")));

        long cleanerId = login("baojie", "CLEANER");
        assertTrue(users.scopes(cleanerId).contains(secondCanteen));
        forbidden(catalog::menus);
        forbidden(catalog::batches);
        forbidden(() -> catalog.addUsage(menuId, Map.of("batchId", batchId, "quantity", 1)));
        forbidden(() -> operations.add(Map.of("canteenId", secondCanteen, "category", "SAMPLE")));
        long operationId = remember("operation_log", operations.add(Map.of(
                "canteenId", secondCanteen, "category", "CLEANING", "operationDate", today.toString(),
                "meal", "NONE", "details", Map.of("area", "后厨地面", "method", "清洗并消毒",
                        "completedAt", today + "T15:00:00", "note", marker))));
        assertEquals("CLEANING", operationDb.one(operationId).get("category"));
        assertEquals(cleanerId, ((Number) operationDb.one(operationId).get("recorded_by")).longValue());
        assertTrue(includes(operations.list(), operationId));
    }

    @AfterTransaction
    void allCreatedBusinessRowsHaveBeenRolledBack() {
        for (Map.Entry<String, Long> entry : createdIds.entrySet()) {
            // Names come only from the fixed table literals in this test.
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + entry.getKey() + " WHERE id=?",
                    Integer.class, entry.getValue()), "事务结束后不应留下 " + entry.getKey() + " 记录");
        }
    }
}
