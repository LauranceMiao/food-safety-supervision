package edu.campus.food.catalog;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class CatalogControllerTest {
    @Test
    void createsBatchWithSimpleServerGeneratedNumber() {
        CatalogMapper db = mock(CatalogMapper.class);
        CurrentUser current = mock(CurrentUser.class);
        AuditService audit = mock(AuditService.class);
        FileService files = mock(FileService.class);
        when(current.id()).thenReturn(30L);
        when(db.ingredient(2L)).thenReturn(Map.of("id", 2L));
        when(db.supplier(3L)).thenReturn(Map.of("id", 3L, "active", true));
        when(db.lockPlan(5L)).thenReturn(Map.of("id", 5L, "canteen_id", 1L,
                "ingredient_id", 2L, "status", "OPEN", "published_at", java.time.LocalDateTime.now()));
        when(db.addBatch(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> values = invocation.getArgument(0);
            assertTrue(String.valueOf(values.get("batchCode")).startsWith("pending-"));
            values.put("id", 14L);
            return 1;
        });
        when(db.setBatchCode(14L, "14")).thenReturn(1);
        when(db.batch(14L)).thenReturn(Map.of("id", 14L, "batch_code", "14"));

        Map<String, Object> request = new HashMap<>();
        request.put("canteenId", 1);
        request.put("ingredientId", 2);
        request.put("supplierId", 3);
        request.put("planId", 5);
        request.put("receivedOn", "2026-09-25");
        request.put("quantity", BigDecimal.ONE);
        request.put("batchCode", "BEEF-ACCEPTED-random");
        Map<String, Object> result = new CatalogController(db, current, audit, files).addBatch(request);

        assertEquals("14", result.get("batch_code"));
        verify(db).setBatchCode(14L, "14");
        verify(audit).log("CREATE_BATCH", "purchase_batch", 14L, "批次编号=14");
    }
}
