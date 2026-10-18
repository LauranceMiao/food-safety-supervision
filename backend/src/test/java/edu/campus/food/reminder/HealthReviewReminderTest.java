package edu.campus.food.reminder;

import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthReviewReminderTest {
    private final AuthService auth = mock(AuthService.class);
    private final ReminderMapper db = mock(ReminderMapper.class);
    private final ReminderController controller = new ReminderController(db, new CurrentUser(auth));

    @BeforeEach void setup() {
        when(db.healthSubmissions()).thenReturn(List.of(
                Map.of("id", 1L, "user_id", 8L, "canteen_id", 2L, "real_name", "厨师", "status", "PENDING"),
                Map.of("id", 2L, "user_id", 9L, "canteen_id", 3L, "real_name", "保洁", "status", "PENDING"),
                Map.of("id", 3L, "user_id", 8L, "canteen_id", 2L, "real_name", "厨师", "status", "REJECTED"),
                Map.of("id", 4L, "user_id", 9L, "canteen_id", 2L, "real_name", "采购", "status", "REJECTED"),
                Map.of("id", 5L, "user_id", 8L, "canteen_id", 2L, "real_name", "厨师", "status", "APPROVED")));
    }

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("account", ""));
        when(auth.profile("account")).thenReturn(Map.of("id", 8L, "roles", List.of(role), "canteenIds", List.of(2L)));
    }

    private List<Object> keys() { return controller.list().stream().map(row -> row.get("key")).toList(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void managerOnlyGetsPendingReviewsInsideAssignedCanteens() {
        login("MANAGER");
        assertEquals(List.of("HEALTH_REVIEW:1"), keys());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "PURCHASER", "CHEF", "CLEANER"})
    void eachWorkerOnlyGetsOwnReturnedAndApprovedResults(String role) {
        login(role);
        when(db.readKeys(8L)).thenReturn(List.of("HEALTH_RETURNED:3"));
        assertEquals(List.of("HEALTH_RETURNED:3", "HEALTH_APPROVED:5"), keys());
        var notices = controller.list();
        assertEquals(true, notices.get(0).get("read"));
        assertEquals("/personnel", notices.get(0).get("target"));
        assertTrue(String.valueOf(notices.get(0).get("title")).contains("退回"));
        controller.read(Map.of("key", "HEALTH_RETURNED:3"));
        verify(db).markRead(8L, "HEALTH_RETURNED:3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INSPECTOR", "PRINCIPAL", "COMMUNITY"})
    void schoolReadersAndCommunityDoNotGetUnapprovedSubmissionNotices(String role) {
        login(role);
        assertTrue(keys().isEmpty());
    }
}
