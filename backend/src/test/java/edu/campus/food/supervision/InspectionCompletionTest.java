package edu.campus.food.supervision;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.catalog.CatalogMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InspectionCompletionTest {
    private final SupervisionMapper db = mock(SupervisionMapper.class);
    private final AdminMapper admin = mock(AdminMapper.class);
    private final AuthService auth = mock(AuthService.class);
    private final AuditService audit = mock(AuditService.class);
    private final InspectionIssueController controller = new InspectionIssueController(db, admin,
            mock(CatalogMapper.class), mock(UserMapper.class), new CurrentUser(auth), audit, mock(FileService.class));

    private void login(String role, long id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("inspector", ""));
        when(auth.profile("inspector")).thenReturn(Map.of("id", id, "roles", List.of(role), "canteenIds", List.of(1L)));
    }

    private Map<String, Object> task(String status) {
        return Map.of("id", 1L, "canteen_id", 1L, "inspector_id", 32L, "template_id", 4L, "status", status);
    }

    private void prepareTask() {
        when(db.inspection(1L)).thenReturn(task("IN_PROGRESS"));
        when(admin.templateItems(4L)).thenReturn(List.of(
                Map.of("id", 10L, "title", "环境卫生", "active", true),
                Map.of("id", 11L, "title", "餐饮具清洗消毒", "active", true),
                Map.of("id", 12L, "title", "停用检查项", "active", false)));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedInspectorSavesEveryActiveItemThenCompletesTask() {
        login("INSPECTOR", 32L);
        prepareTask();
        when(db.inspection(1L)).thenReturn(task("IN_PROGRESS"), task("COMPLETED"));
        when(db.completeInspection(1L, "无问题")).thenReturn(1);
        List<Map<String, Object>> saved = List.of(
                Map.of("item_id", 10L, "result", "PASS", "note", ""),
                Map.of("item_id", 11L, "result", "NA", "note", "未使用"));
        when(db.inspectionResults(1L)).thenReturn(saved);

        Map<String, Object> response = controller.completeInspection(1L, Map.of("note", "无问题", "results", List.of(
                Map.of("itemId", 10L, "result", "PASS", "note", ""),
                Map.of("itemId", 11L, "result", "NA", "note", "未使用"))));

        assertEquals("COMPLETED", response.get("status"));
        assertEquals(saved, response.get("results"));
        verify(db).addInspectionResult(Map.of("inspectionId", 1L, "itemId", 10L,
                "itemTitle", "环境卫生", "result", "PASS", "note", ""));
        verify(db).addInspectionResult(Map.of("inspectionId", 1L, "itemId", 11L,
                "itemTitle", "餐饮具清洗消毒", "result", "NA", "note", "未使用"));
        verify(db, times(2)).addInspectionResult(anyMap());
        verify(db).completeInspection(1L, "无问题");
        verify(audit).log("COMPLETE_INSPECTION", "inspection", 1L, "完成检查");
    }

    @Test
    void failedItemWithoutExplanationIsRejectedBeforeSaving() {
        login("INSPECTOR", 32L);
        prepareTask();
        ApiException error = assertThrows(ApiException.class, () -> controller.completeInspection(1L,
                Map.of("results", List.of(Map.of("itemId", 10L, "result", "FAIL", "note", "  "),
                        Map.of("itemId", 11L, "result", "PASS")))));
        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("异常检查项必须填写说明", error.getMessage());
        verify(db, never()).addInspectionResult(anyMap());
        verify(db, never()).completeInspection(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void missingChecklistItemIsRejectedBeforeSaving() {
        login("INSPECTOR", 32L);
        prepareTask();
        ApiException error = assertThrows(ApiException.class, () -> controller.completeInspection(1L,
                Map.of("results", List.of(Map.of("itemId", 10L, "result", "PASS")))));
        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("必须逐项填写全部检查项目", error.getMessage());
        verify(db, never()).addInspectionResult(anyMap());
        verify(db, never()).completeInspection(anyLong(), anyString());
    }

    @Test
    void inspectorCannotCompleteAnotherInspectorsTask() {
        login("INSPECTOR", 99L);
        prepareTask();
        ApiException error = assertThrows(ApiException.class, () -> controller.completeInspection(1L, Map.of()));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
        assertEquals("只能处理分配给自己的检查任务", error.getMessage());
        verify(db, never()).addInspectionResult(anyMap());
        verify(db, never()).completeInspection(anyLong(), anyString());
    }

    @Test
    void managerCannotCompleteAnInspection() {
        login("MANAGER", 32L);
        ApiException error = assertThrows(ApiException.class, () -> controller.completeInspection(1L, Map.of()));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(db, admin, audit);
    }
}
