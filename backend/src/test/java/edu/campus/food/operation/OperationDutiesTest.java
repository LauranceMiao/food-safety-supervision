package edu.campus.food.operation;

import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class OperationDutiesTest {
    private final OperationMapper db = mock(OperationMapper.class);
    private final AuthService auth = mock(AuthService.class);
    private final OperationController controller = new OperationController(db, new CurrentUser(auth),
            mock(AuditService.class), JsonMapper.builder().build());

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("worker", ""));
        when(auth.profile("worker")).thenReturn(Map.of("id", 8L, "roles", List.of(role), "canteenIds", List.of(2L)));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void staffReadOnlyTheirOperationCategoriesInAssignedCanteen() {
        Map<String, Object> sample = Map.of("id", 1L, "canteen_id", 2L, "category", "SAMPLE");
        Map<String, Object> cleaning = Map.of("id", 2L, "canteen_id", 2L, "category", "CLEANING");
        Map<String, Object> disinfection = Map.of("id", 3L, "canteen_id", 2L, "category", "DISINFECTION");
        when(db.all()).thenReturn(List.of(sample, cleaning, disinfection,
                Map.of("id", 4L, "canteen_id", 3L, "category", "CLEANING")));
        login("CHEF");
        assertEquals(List.of(sample), controller.list());
        login("CLEANER");
        assertEquals(List.of(cleaning, disinfection), controller.list());
        login("MANAGER");
        assertEquals(List.of(sample, cleaning, disinfection), controller.list());
        login("PURCHASER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, controller::list).status());
    }

    @Test
    void usersCannotWriteAnotherPositionsOperation() {
        login("CHEF");
        for (String category : List.of("CLEANING", "DISINFECTION")) {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.add(Map.of("canteenId", 2, "category", category))).status());
        }
        login("CLEANER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.add(Map.of("canteenId", 2, "category", "SAMPLE"))).status());
        for (String role : List.of("MANAGER", "PURCHASER", "STAFF")) {
            login(role);
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                    () -> controller.add(Map.of())).status());
        }
        verifyNoInteractions(db);
    }

    @Test
    void cleanerCanRecordCleaningWithoutChefAccess() {
        login("CLEANER");
        when(db.add(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> values = invocation.getArgument(0);
            assertEquals("CLEANING", values.get("category"));
            assertEquals(8L, values.get("recordedBy"));
            values.put("id", 12L);
            return 1;
        });
        when(db.one(12L)).thenReturn(Map.of("id", 12L, "category", "CLEANING"));
        assertEquals("CLEANING", controller.add(Map.of("canteenId", 2, "category", "CLEANING",
                "operationDate", "2026-09-26", "meal", "NONE", "details",
                Map.of("area", "后厨地面", "method", "清洗并消毒", "completedAt", "2026-09-26T15:00:00"))).get("category"));
    }
}
