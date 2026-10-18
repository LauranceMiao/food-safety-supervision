package edu.campus.food.catalog;

import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class CatalogDutiesTest {
    private final CatalogMapper db = mock(CatalogMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final FileService files = mock(FileService.class);
    private final AuthService auth = mock(AuthService.class);
    private final CurrentUser current = new CurrentUser(auth);
    private final CatalogController controller = new CatalogController(db, current, audit, files);

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("worker", ""));
        when(auth.profile("worker")).thenReturn(Map.of("id", 8L, "roles", List.of(role), "canteenIds", List.of(2L)));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private Map<String, Object> plan(boolean published, Long supplierId) {
        Map<String, Object> values = new HashMap<>(Map.of("id", 5L, "canteen_id", 2L,
                "ingredient_id", 11L, "status", "OPEN"));
        if (published) values.put("published_at", LocalDateTime.now());
        if (supplierId != null) values.put("supplier_id", supplierId);
        return values;
    }

    private Map<String, Object> arrival() {
        return Map.of("canteenId", 2, "ingredientId", 11, "supplierId", 3,
                "planId", 5, "receivedOn", "2026-09-26", "quantity", 10);
    }

    @Test
    void purchaserSeesPublishedDemandOnlyInsideAssignedCanteen() {
        login("PURCHASER");
        Map<String, Object> published = plan(true, null);
        Map<String, Object> otherCanteen = new HashMap<>(published);
        otherCanteen.put("canteen_id", 3L);
        when(db.plans()).thenReturn(List.of(plan(false, null), published, otherCanteen));
        assertEquals(List.of(published), controller.plans());
    }

    @Test
    void planningAndExecutionRolesCannotPerformEachOthersActions() {
        for (String role : List.of("MANAGER", "CHEF", "CLEANER", "STAFF")) {
            login(role);
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.addBatch(arrival())).status());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.accept(1L, Map.of("result", "ACCEPTED"))).status());
        }
        for (String role : List.of("MANAGER", "PURCHASER", "CLEANER", "STAFF")) {
            login(role);
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.prepareMenu(1L)).status());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.addUsage(1L, Map.of())).status());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.serve(1L)).status());
        }
        login("PURCHASER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.addPlan(Map.of())).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.publishPlan(5L)).status());
        verifyNoInteractions(db);
    }

    @Test
    void managerCanSaveDemandWithoutChoosingSupplierAndThenPublish() {
        login("MANAGER");
        when(db.ingredient(11L)).thenReturn(Map.of("id", 11L));
        when(db.addPlan(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> values = invocation.getArgument(0);
            assertNull(values.get("supplierId"));
            assertEquals(8L, values.get("createdBy"));
            values.put("id", 5L);
            return 1;
        });
        when(db.plan(5L)).thenReturn(plan(false, null));
        assertNull(controller.addPlan(Map.of("canteenId", 2, "ingredientId", 11,
                "plannedOn", "2026-09-26", "plannedQuantity", 10)).get("published_at"));
        verify(db, never()).publishPlan(anyLong(), anyLong());

        when(db.lockPlan(5L)).thenReturn(plan(false, null));
        when(db.publishPlan(5L, 8L)).thenReturn(1);
        when(db.plan(5L)).thenReturn(plan(true, null));
        assertNotNull(controller.publishPlan(5L).get("published_at"));
        verify(audit).log("PUBLISH_PURCHASE_PLAN", "purchase_plan", 5L, "发布采购需求");
    }

    @Test
    void purchaserCannotRegisterUnpublishedOrMismatchedDemand() {
        login("PURCHASER");
        when(db.ingredient(11L)).thenReturn(Map.of("id", 11L));
        when(db.supplier(3L)).thenReturn(Map.of("id", 3L, "active", true));
        when(db.lockPlan(5L)).thenReturn(plan(false, null));
        assertThrows(ApiException.class, () -> controller.addBatch(arrival()));
        when(db.lockPlan(5L)).thenReturn(plan(true, 9L));
        assertThrows(ApiException.class, () -> controller.addBatch(arrival()));
        Map<String, Object> wrongCanteen = plan(true, null);
        wrongCanteen.put("canteen_id", 3L);
        when(db.lockPlan(5L)).thenReturn(wrongCanteen);
        assertThrows(ApiException.class, () -> controller.addBatch(arrival()));
        when(db.supplier(3L)).thenReturn(Map.of("id", 3L, "active", false));
        assertThrows(ApiException.class, () -> controller.addBatch(arrival()));
        verify(db, never()).addBatch(anyMap());
    }

    @Test
    void registeredArrivalMayFinishAfterDemandClosureButNotCancellation() {
        login("PURCHASER");
        when(db.lockBatch(20L)).thenReturn(Map.of("id", 20L, "canteen_id", 2L, "status", "PENDING",
                "plan_id", 5L, "ingredient_id", 11L, "supplier_id", 3L));
        Map<String, Object> cancelled = plan(true, null);
        cancelled.put("status", "CANCELLED");
        when(db.lockPlan(5L)).thenReturn(cancelled);
        assertThrows(ApiException.class, () -> controller.accept(20L, Map.of("result", "ACCEPTED")));
        verify(db, never()).addAcceptance(anyMap());
        Map<String, Object> closed = plan(true, null);
        closed.put("status", "CLOSED");
        when(db.lockPlan(5L)).thenReturn(closed);
        when(db.batch(20L)).thenReturn(Map.of("id", 20L, "canteen_id", 2L));
        when(db.acceptances(20L)).thenReturn(List.of());
        when(db.usagesByBatch(20L)).thenReturn(List.of());
        assertEquals(20L, controller.accept(20L, Map.of("result", "ACCEPTED")).get("id"));
        verify(db).setBatchStatus(20L, "ACCEPTED");
    }

    @Test
    void onlyMigrationMarkedLegacyUnplannedArrivalsCanFinishAcceptance() {
        login("PURCHASER");
        Map<String, Object> arrival = new HashMap<>(Map.of("id", 20L, "canteen_id", 2L, "status", "PENDING"));
        when(db.lockBatch(20L)).thenReturn(arrival);
        assertThrows(ApiException.class, () -> controller.accept(20L, Map.of("result", "ACCEPTED")));
        verify(db, never()).addAcceptance(anyMap());
        arrival.put("legacy_unplanned", true);
        when(db.batch(20L)).thenReturn(Map.of("id", 20L, "canteen_id", 2L));
        when(db.acceptances(20L)).thenReturn(List.of());
        when(db.usagesByBatch(20L)).thenReturn(List.of());
        assertEquals(20L, controller.accept(20L, Map.of("result", "ACCEPTED")).get("id"));
        verify(audit).log("ACCEPT_BATCH", "purchase_batch", 20L, "历史无需求批次验收：ACCEPTED ");
    }

    @Test
    void chefSeesPublishedRecipesAndAcceptedStockButCannotReadProcurementCatalog() {
        login("CHEF");
        Map<String, Object> draft = Map.of("id", 1L, "canteen_id", 2L);
        Map<String, Object> published = Map.of("id", 2L, "canteen_id", 2L, "published_at", LocalDateTime.now());
        when(db.menus()).thenReturn(List.of(draft, published));
        assertEquals(List.of(published), controller.menus());
        Map<String, Object> accepted = Map.of("id", 3L, "canteen_id", 2L, "status", "ACCEPTED");
        when(db.batches()).thenReturn(List.of(accepted, Map.of("id", 4L, "canteen_id", 2L, "status", "PENDING")));
        assertEquals(List.of(accepted), controller.batches());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::ingredients).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::suppliers).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::plans).status());
    }

    @Test
    void cleanerCannotReadKitchenOrProcurementData() {
        login("CLEANER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::menus).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::dishes).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::batches).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::suppliers).status());
        verifyNoInteractions(db);
    }

    @Test
    void chefCannotConfirmCookingForOtherCanteen() {
        login("CHEF");
        when(db.lockMenu(7L)).thenReturn(Map.of("id", 7L, "canteen_id", 3L));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> controller.prepareMenu(7L)).status());
        verify(db, never()).markPrepared(anyLong(), anyLong());
    }
}
