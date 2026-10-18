package edu.campus.food.auth;

import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import edu.campus.food.personnel.PersonnelController;
import edu.campus.food.personnel.PersonnelMapper;
import edu.campus.food.reminder.ReminderController;
import edu.campus.food.reminder.ReminderMapper;
import edu.campus.food.supervision.FeedbackController;
import edu.campus.food.supervision.SupervisionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkVisibilityTest {
    private final AuthService auth = mock(AuthService.class);
    private final CurrentUser current = new CurrentUser(auth);

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("worker", ""));
        when(auth.profile("worker")).thenReturn(Map.of("id", 8L, "roles", List.of(role), "canteenIds", List.of(2L)));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"PURCHASER", "CHEF", "CLEANER"})
    void personnelShowsOnlyTheWorkersOwnProfileInsideTheirCanteen(String role) {
        login(role);
        PersonnelMapper db = mock(PersonnelMapper.class);
        Map<String, Object> own = Map.of("user_id", 8L, "canteen_id", 2L, "display_name", "本人", "approved_submission_id", 10L);
        when(db.all()).thenReturn(List.of(own,
                Map.of("user_id", 9L, "canteen_id", 2L, "display_name", "其他人员", "approved_submission_id", 11L),
                Map.of("user_id", 8L, "canteen_id", 3L, "display_name", "其他食堂档案", "approved_submission_id", 12L),
                Map.of("user_id", 8L, "canteen_id", 2L, "display_name", "未经批准的旧档案")));
        PersonnelController controller = new PersonnelController(db, current,
                mock(FileService.class), mock(AuditService.class));
        assertEquals(List.of(own), controller.list());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.candidates(2L)).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PURCHASER", "CHEF", "CLEANER"})
    void feedbackShowsOnlyAssignedTasksInsideTheWorkersCanteen(String role) {
        login(role);
        SupervisionMapper db = mock(SupervisionMapper.class);
        FeedbackController controller = new FeedbackController(db, mock(UserMapper.class), current,
                mock(AuditService.class), mock(FileService.class));
        Map<String, Object> own = Map.of("id", 1L, "canteen_id", 2L, "assigned_to", 8L,
                "user_id", 100L, "status", "PROCESSING");
        Map<String, Object> others = Map.of("id", 2L, "canteen_id", 2L, "assigned_to", 9L,
                "user_id", 100L, "status", "PROCESSING");
        Map<String, Object> outOfScope = Map.of("id", 3L, "canteen_id", 3L, "assigned_to", 8L,
                "user_id", 100L, "status", "PROCESSING");
        when(db.feedbacks()).thenReturn(List.of(own, others, outOfScope,
                Map.of("id", 4L, "canteen_id", 2L, "user_id", 100L, "status", "NEW")));
        when(db.feedback(1L)).thenReturn(own);
        when(db.feedback(2L)).thenReturn(others);
        when(db.feedback(3L)).thenReturn(outOfScope);
        assertEquals(List.of(own), controller.list());
        assertEquals(own, controller.detail(1L));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.detail(2L)).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.detail(3L)).status());
        when(db.investigateFeedback(1L, "已核实并完成处理", 8L)).thenReturn(1);
        assertEquals(own, controller.investigate(1L, Map.of("note", "已核实并完成处理")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> controller.investigate(2L, Map.of("note", "尝试操作其他人的反馈"))).status());
        verify(db, never()).investigateFeedback(eq(2L), anyString(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PURCHASER", "CHEF", "CLEANER"})
    void managerCanAssignFeedbackToEachNewPosition(String assigneeRole) {
        login("MANAGER");
        SupervisionMapper db = mock(SupervisionMapper.class);
        UserMapper users = mock(UserMapper.class);
        FeedbackController controller = new FeedbackController(db, users, current,
                mock(AuditService.class), mock(FileService.class));
        Map<String, Object> feedback = Map.of("id", 1L, "canteen_id", 2L, "user_id", 100L, "status", "NEW");
        when(db.feedback(1L)).thenReturn(feedback);
        when(users.roles(9L)).thenReturn(List.of(assigneeRole));
        when(users.byId(9L)).thenReturn(Map.of("id", 9L, "active", true));
        when(users.scopes(9L)).thenReturn(List.of(2L));
        when(db.assignFeedback(1L, 9L)).thenReturn(1);
        assertEquals(feedback, controller.assign(1L, Map.of("assignedTo", 9L)));
        verify(db).assignFeedback(1L, 9L);

        when(users.scopes(9L)).thenReturn(List.of(3L));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> controller.assign(1L, Map.of("assignedTo", 9L))).status());
        verify(db, times(1)).assignFeedback(1L, 9L);
    }

    @Test
    void cleanerReceivesOwnRectificationOnlyAndNoProcurementOrInspectionReminders() {
        login("CLEANER");
        ReminderMapper db = mock(ReminderMapper.class);
        ReminderController controller = new ReminderController(db, current);
        LocalDate dueDate = LocalDate.of(2026, 9, 26);
        LocalDateTime dueAt = dueDate.atTime(9, 0);
        when(db.suppliers()).thenReturn(List.of(Map.of("id", 1L, "name", "供应商", "license_expires_on", dueDate)));
        when(db.batches()).thenReturn(List.of(Map.of("id", 1L, "canteen_id", 2L,
                "batch_code", "1", "expires_on", dueDate)));
        when(db.inspections()).thenReturn(List.of(Map.of("id", 1L, "canteen_id", 2L,
                "inspector_id", 8L, "scheduled_at", dueAt)));
        when(db.overdueIssues()).thenReturn(List.of(
                Map.of("id", 1L, "canteen_id", 2L, "assigned_to", 8L, "due_at", dueAt),
                Map.of("id", 2L, "canteen_id", 2L, "assigned_to", 9L, "due_at", dueAt),
                Map.of("id", 3L, "canteen_id", 3L, "assigned_to", 8L, "due_at", dueAt)));
        when(db.readKeys(8L)).thenReturn(List.of());
        List<Map<String, Object>> reminders = controller.list();
        assertEquals(List.of("ISSUE:1:OVERDUE"), reminders.stream().map(row -> row.get("key")).toList());
        assertEquals(false, reminders.get(0).get("read"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> controller.read(Map.of("key", "BATCH:1:" + dueDate))).status());
        verify(db, never()).markRead(anyLong(), anyString());
    }
}
