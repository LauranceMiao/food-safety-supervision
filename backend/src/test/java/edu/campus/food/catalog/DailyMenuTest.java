package edu.campus.food.catalog;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class DailyMenuTest {
    private final CatalogMapper db = mock(CatalogMapper.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final AuditService audit = mock(AuditService.class);
    private final FileService files = mock(FileService.class);
    private final CatalogController controller = new CatalogController(db, current, audit, files);

    @Test
    void managerCanCreateSeveralDishesInOneDailyDraft() {
        when(current.id()).thenReturn(5L);
        when(db.dish(11L)).thenReturn(Map.of("id", 11L, "canteen_id", 2L));
        when(db.dish(12L)).thenReturn(Map.of("id", 12L, "canteen_id", 2L));
        when(db.addMenu(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> values = invocation.getArgument(0);
            values.put("id", values.get("dishId"));
            return 1;
        });
        when(db.menu(11L)).thenReturn(Map.of("id", 11L));
        when(db.menu(12L)).thenReturn(Map.of("id", 12L));

        Map<String, Object> request = Map.of("canteenId", 2, "serviceDate", "2026-09-25", "items", List.of(
                Map.of("dishId", 11, "meal", "LUNCH", "cookingNote", "少盐", "targetPortions", 100),
                Map.of("dishId", 12, "meal", "DINNER")));
        assertEquals(2, controller.addDailyMenu(request).size());
        verify(db, times(2)).addMenu(anyMap());
    }

    @Test
    void chefCannotSeeUnpublishedDrafts() {
        when(current.has("CHEF")).thenReturn(true);
        Map<String, Object> draft = new HashMap<>(Map.of("id", 1L));
        Map<String, Object> published = new HashMap<>(Map.of("id", 2L, "published_at", LocalDateTime.now()));
        when(db.menus()).thenReturn(List.of(draft, published));
        when(current.visibleCanteens(anyList())).thenReturn(List.of(draft, published));
        assertEquals(List.of(published), controller.menus());
    }

    @Test
    void chefConfirmsCookingThroughExplicitRole() {
        when(current.id()).thenReturn(8L);
        when(current.has("CHEF")).thenReturn(true);
        when(db.lockMenu(7L)).thenReturn(Map.of("id", 7L, "canteen_id", 2L));
        when(db.markPrepared(7L, 8L)).thenReturn(1);
        when(db.menu(7L)).thenReturn(Map.of("id", 7L));
        assertEquals(7L, controller.prepareMenu(7L).get("id"));
        verify(current).requireRole("CHEF");
        verify(db).markPrepared(7L, 8L);
    }

    @Test
    void publishedMenuCannotBeEditedAsDraft() {
        when(db.lockMenu(9L)).thenReturn(Map.of(
                "id", 9L, "canteen_id", 2L, "status", "PLANNED",
                "service_date", LocalDate.of(2026, 9, 25), "published_at", LocalDateTime.now()));
        assertThrows(ApiException.class, () -> controller.updateDraftMenu(9L,
                Map.of("dishId", 11, "meal", "LUNCH")));
        verify(db, never()).updateDraftMenu(anyMap());
    }

    @Test
    void managerCanChangeAnUnpublishedDish() {
        Map<String, Object> previous = new HashMap<>();
        previous.put("id", 9L);
        previous.put("canteen_id", 2L);
        previous.put("status", "PLANNED");
        previous.put("service_date", LocalDate.of(2026, 9, 25));
        when(db.lockMenu(9L)).thenReturn(previous);
        when(db.dish(11L)).thenReturn(Map.of("id", 11L, "canteen_id", 2L));
        when(db.updateDraftMenu(anyMap())).thenReturn(1);
        when(db.menu(9L)).thenReturn(Map.of("id", 9L, "cooking_note", "少盐"));

        Map<String, Object> result = controller.updateDraftMenu(9L,
                Map.of("dishId", 11, "meal", "LUNCH", "cookingNote", "少盐"));
        assertEquals("少盐", result.get("cooking_note"));
        verify(db).updateDraftMenu(argThat(value -> Long.valueOf(9L).equals(value.get("id"))
                && "少盐".equals(value.get("cookingNote"))));
    }
}
