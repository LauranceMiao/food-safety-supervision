package edu.campus.food.personnel;

import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import edu.campus.food.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonnelReviewTest {
    private final AuthService auth = mock(AuthService.class);
    private final CurrentUser current = new CurrentUser(auth);
    private final PersonnelMapper db = mock(PersonnelMapper.class);
    private final FileService files = mock(FileService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PersonnelController controller = new PersonnelController(db, current, files, audit);

    private void login(long id, String role, List<Long> scopes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("person", ""));
        when(auth.profile("person")).thenReturn(Map.of("id", id, "roles", List.of(role), "canteenIds", scopes));
    }

    private Map<String, Object> row(String status) {
        Map<String, Object> value = new HashMap<>();
        value.put("id", 10L);
        value.put("user_id", 8L);
        value.put("canteen_id", 2L);
        value.put("status", status);
        value.put("version", 3);
        value.put("real_name", "张厨师");
        value.put("contact_phone", "13800000000");
        value.put("position_name", "厨师");
        value.put("health_cert_no", "JK001");
        value.put("health_expires_on", LocalDate.of(2027, 9, 26));
        value.put("certificate_path", "/api/files/own-proof");
        return value;
    }

    private void error(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ApiException.class, action::run).status());
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "PURCHASER", "CHEF", "CLEANER"})
    void eachWorkerCanSaveIncompleteOwnDraftAndCannotForgeItsOwner(String role) {
        login(8L, role, List.of(2L));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        doAnswer(invocation -> {
            Map<String, Object> value = invocation.getArgument(0);
            assertEquals(8L, value.get("userId"));
            assertEquals("", value.get("realName"));
            value.put("id", 10L);
            return 1;
        }).when(db).createDraft(anyMap());
        when(db.submission(10L)).thenReturn(row("DRAFT"));
        assertEquals("DRAFT", controller.createDraft(Map.of("canteenId", 2L, "userId", 999L)).get("status"));
        verify(db).lockUser(8L);
        verify(db, never()).approveSnapshot(anyLong(), anyLong());
        verify(audit).log("SAVE_STAFF_PROFILE_DRAFT", "staff_profile_submission", 10L, "version=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "INSPECTOR", "PRINCIPAL", "ADMIN", "COMMUNITY"})
    void nonWorkersCannotCreateOrSubmitHealthProfiles(String role) {
        login(8L, role, List.of(2L));
        error(HttpStatus.FORBIDDEN, () -> controller.createDraft(Map.of("canteenId", 2L)));
        error(HttpStatus.FORBIDDEN, () -> controller.submit(10L, Map.of("version", 3)));
        verifyNoInteractions(db, files);
    }

    @Test
    void duplicateDraftAndPendingApplicationsBlockNewDraft() {
        login(8L, "CHEF", List.of(2L));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        for (String status : List.of("DRAFT", "PENDING")) {
            when(db.latest(8L, 2L)).thenReturn(row(status));
            error(HttpStatus.CONFLICT, () -> controller.createDraft(Map.of("canteenId", 2L)));
        }
        verify(db, never()).createDraft(anyMap());
    }

    @Test
    void workersCannotEditOtherPeopleOrMoveTheDraftToAnotherCanteen() {
        login(9L, "CLEANER", List.of(2L));
        when(db.lockSubmission(10L)).thenReturn(row("DRAFT"));
        error(HttpStatus.FORBIDDEN, () -> controller.updateDraft(10L, Map.of("canteenId", 2L, "version", 3)));
        login(8L, "CHEF", List.of(2L));
        error(HttpStatus.FORBIDDEN, () -> controller.createDraft(Map.of("canteenId", 3L)));
        error(HttpStatus.BAD_REQUEST, () -> controller.updateDraft(10L, Map.of("canteenId", 3L, "version", 3)));
        verify(db, never()).updateDraft(anyMap());
    }

    @Test
    void incompleteAndForeignProofCannotBeSubmitted() {
        login(8L, "CHEF", List.of(2L));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        for (String key : List.of("real_name", "position_name", "health_cert_no", "health_expires_on", "certificate_path")) {
            Map<String, Object> value = row("DRAFT");
            value.remove(key);
            when(db.lockSubmission(10L)).thenReturn(value);
            error(HttpStatus.BAD_REQUEST, () -> controller.submit(10L, Map.of("version", 3)));
        }
        when(db.lockSubmission(10L)).thenReturn(row("DRAFT"));
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "附件非本人上传"))
                .when(files).requireOwned("/api/files/own-proof", "STAFF_HEALTH_CERT", 2L);
        error(HttpStatus.BAD_REQUEST, () -> controller.submit(10L, Map.of("version", 3)));
        verify(db, never()).submit(anyLong(), anyLong(), anyLong());
    }

    @Test
    void submissionTransitionsToPendingWithoutPublishingTheSnapshot() {
        login(8L, "CHEF", List.of(2L));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        when(db.lockSubmission(10L)).thenReturn(row("DRAFT"));
        when(db.submit(10L, 8L, 3L)).thenReturn(1);
        when(db.submission(10L)).thenReturn(row("PENDING"));
        assertEquals("PENDING", controller.submit(10L, Map.of("version", 3)).get("status"));
        verify(files).requireOwned("/api/files/own-proof", "STAFF_HEALTH_CERT", 2L);
        verify(db, never()).approveSnapshot(anyLong(), anyLong());
        verify(audit).log("SUBMIT_STAFF_PROFILE", "staff_profile_submission", 10L, "version=4");
    }

    @Test
    void staleVersionsAndAlreadySubmittedDraftsCannotBeChanged() {
        login(8L, "CHEF", List.of(2L));
        when(db.lockSubmission(10L)).thenReturn(row("DRAFT"));
        error(HttpStatus.CONFLICT, () -> controller.updateDraft(10L, Map.of("canteenId", 2L, "version", 2)));
        error(HttpStatus.CONFLICT, () -> controller.submit(10L, Map.of("version", 2)));
        when(db.lockSubmission(10L)).thenReturn(row("PENDING"));
        error(HttpStatus.CONFLICT, () -> controller.updateDraft(10L, Map.of("canteenId", 2L, "version", 3)));
        verify(db, never()).updateDraft(anyMap());
        verify(db, never()).submit(anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PURCHASER", "CHEF", "CLEANER", "INSPECTOR", "PRINCIPAL", "ADMIN"})
    void onlyManagerCanReview(String role) {
        login(9L, role, List.of(2L));
        error(HttpStatus.FORBIDDEN, () -> controller.review(10L, Map.of("decision", "APPROVED", "version", 3)));
        error(HttpStatus.FORBIDDEN, controller::reviews);
        verifyNoInteractions(db);
    }

    @Test
    void managerCannotReviewOutsideAssignedCanteensOrReviewOwnApplication() {
        login(9L, "MANAGER", List.of(3L));
        when(db.lockSubmission(10L)).thenReturn(row("PENDING"));
        error(HttpStatus.FORBIDDEN, () -> controller.review(10L, Map.of("decision", "APPROVED", "version", 3)));
        login(8L, "MANAGER", List.of(2L));
        error(HttpStatus.FORBIDDEN, () -> controller.review(10L, Map.of("decision", "APPROVED", "version", 3)));
        verify(db, never()).review(anyMap());
    }

    @Test
    void rejectionRequiresReasonAndKeepsThePreviousApprovedSnapshot() {
        login(9L, "MANAGER", List.of(2L));
        when(db.lockSubmission(10L)).thenReturn(row("PENDING"));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        error(HttpStatus.BAD_REQUEST, () -> controller.review(10L, Map.of("decision", "REJECTED", "version", 3, "note", " ")));
        when(db.review(anyMap())).thenReturn(1);
        when(db.submission(10L)).thenReturn(row("REJECTED"));
        assertEquals("REJECTED", controller.review(10L, Map.of("decision", "REJECTED", "version", 3, "note", "证明照片不清晰，请重新上传")).get("status"));
        verify(db, never()).approveSnapshot(anyLong(), anyLong());
        verify(audit).log("REJECT_STAFF_PROFILE", "staff_profile_submission", 10L, "version=4,decision=REJECTED");
    }

    @Test
    void approvalPublishesSnapshotAndDuplicateOrStaleReviewsFail() {
        login(9L, "MANAGER", List.of(2L));
        when(db.lockSubmission(10L)).thenReturn(row("PENDING"));
        when(db.workerAt(8L, 2L)).thenReturn(1);
        error(HttpStatus.CONFLICT, () -> controller.review(10L, Map.of("decision", "APPROVED", "version", 2)));
        when(db.review(anyMap())).thenReturn(1);
        when(db.submission(10L)).thenReturn(row("APPROVED"));
        when(db.approveSnapshot(10L, 9L)).thenReturn(1);
        assertEquals("APPROVED", controller.review(10L, Map.of("decision", "APPROVED", "version", 3)).get("status"));
        verify(db).approveSnapshot(10L, 9L);
        verify(audit).log("APPROVE_STAFF_PROFILE", "staff_profile_submission", 10L, "version=4,decision=APPROVED");
        when(db.lockSubmission(10L)).thenReturn(row("APPROVED"));
        error(HttpStatus.CONFLICT, () -> controller.review(10L, Map.of("decision", "APPROVED", "version", 3)));
        verify(db, times(1)).review(anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSPECTOR", "PRINCIPAL", "MANAGER", "ADMIN"})
    void supervisorsNeverSeeUnapprovedProfilesInNormalPersonnelList(String role) {
        login(9L, role, List.of(2L));
        Map<String, Object> approved = row("APPROVED");
        approved.put("approved_submission_id", 10L);
        when(db.all()).thenReturn(List.of(approved, row("PENDING"), row("DRAFT"), row("REJECTED")));
        assertEquals(List.of(approved), controller.list());
    }

    @Test
    void managerCannotUseLegacyDirectSaveToBypassReview() {
        login(9L, "MANAGER", List.of(2L));
        error(HttpStatus.CONFLICT, () -> controller.save(8L, 2L, Map.of("positionName", "厨师")));
        verifyNoInteractions(db, files, audit);
    }
}
