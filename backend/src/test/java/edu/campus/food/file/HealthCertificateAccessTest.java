package edu.campus.food.file;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class HealthCertificateAccessTest {
    @TempDir Path uploadDir;
    private final AuthService auth = mock(AuthService.class);
    private final FileMapper db = mock(FileMapper.class);
    private final AdminMapper admin = mock(AdminMapper.class);
    private FileService files;

    @BeforeEach
    void setup() throws Exception {
        files = new FileService(db, admin, new CurrentUser(auth), mock(AuditService.class), uploadDir.toString());
        when(db.byId("proof")).thenReturn(Map.of("owner_id", 8L, "canteen_id", 2L,
                "purpose", "STAFF_HEALTH_CERT", "content_type", "application/pdf", "size_bytes", 5L));
        Files.writeString(uploadDir.resolve("proof"), "%PDF-");
        when(admin.canteen(2L)).thenReturn(Map.of("id", 2L));
    }

    private void login(String role, long userId, long canteenId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("account", ""));
        when(auth.profile("account")).thenReturn(Map.of("id", userId, "roles", List.of(role), "canteenIds", List.of(canteenId)));
    }

    private void link(String status) {
        when(db.healthCertificateLinks("/api/files/proof")).thenReturn(List.of(
                Map.of("user_id", 8L, "canteen_id", 2L, "status", status)));
        when(db.healthCertificateUseCount("/api/files/proof")).thenReturn(1);
    }

    private void denyDownload() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> files.download("proof")).status());
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PRINCIPAL", "INSPECTOR"})
    void schoolReadersSeeApprovedProofOnly(String role) throws Exception {
        login(role, 50L, 3L);
        for (String status : List.of("DRAFT", "PENDING", "REJECTED")) { link(status); denyDownload(); }
        link("APPROVED");
        assertEquals(HttpStatus.OK, files.download("proof").getStatusCode());
    }

    @Test void managerCanReviewSubmittedProofButNotSomeoneElsesDraft() throws Exception {
        login("MANAGER", 50L, 2L);
        link("DRAFT"); denyDownload();
        for (String status : List.of("PENDING", "REJECTED", "APPROVED")) {
            link(status); assertEquals(HttpStatus.OK, files.download("proof").getStatusCode());
        }
        login("MANAGER", 50L, 3L);
        denyDownload();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "PURCHASER", "CHEF", "CLEANER"})
    void workersCanUploadAndReadOwnProofButCannotReadColleaguesProof(String role) throws Exception {
        login(role, 8L, 2L);
        assertEquals(HttpStatus.OK, files.download("proof").getStatusCode());
        var uploaded = files.upload(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-".getBytes()),
                "STAFF_HEALTH_CERT", 2L);
        assertTrue(String.valueOf(uploaded.get("path")).startsWith("/api/files/"));
        verify(db).insert(argThat(row -> Long.valueOf(8L).equals(row.get("ownerId"))
                && "STAFF_HEALTH_CERT".equals(row.get("purpose"))));
        login(role, 9L, 2L);
        for (String status : List.of("DRAFT", "PENDING", "APPROVED", "REJECTED")) { link(status); denyDownload(); }
    }

    @Test void unlinkedHealthProofIsOnlyVisibleToItsUploader() {
        login("MANAGER", 50L, 2L); denyDownload();
        login("INSPECTOR", 50L, 2L); denyDownload();
    }

    @Test void legacyProofOwnerAndSubjectCanReadTheirPreservedDraft() throws Exception {
        when(db.byId("proof")).thenReturn(Map.of("owner_id", 50L, "canteen_id", 2L,
                "purpose", "STAFF_HEALTH_CERT", "content_type", "application/pdf", "size_bytes", 5L));
        link("DRAFT");
        login("CHEF", 8L, 2L);
        assertEquals(HttpStatus.OK, files.download("proof").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> files.requireOwned("/api/files/proof", "STAFF_HEALTH_CERT", 2L)).status());
        for (String oldUploaderRole : List.of("MANAGER", "COMMUNITY", "CHEF")) {
            login(oldUploaderRole, 50L, 2L);
            denyDownload();
        }
        // A revoked subject assignment also prevents the former uploader from
        // treating a historically linked proof as a new personal upload.
        when(db.healthCertificateLinks("/api/files/proof")).thenReturn(List.of());
        denyDownload();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PRINCIPAL", "INSPECTOR"})
    void globalRoleDoesNotExpandTheWorkersHealthUploadScope(String globalRole) {
        login("CHEF", 8L, 3L);
        when(auth.profile("account")).thenReturn(Map.of("id", 8L,
                "roles", List.of("CHEF", globalRole), "canteenIds", List.of(3L)));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> files.upload(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-".getBytes()),
                        "STAFF_HEALTH_CERT", 2L)).status());
        verify(db, never()).insert(anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "PRINCIPAL", "INSPECTOR", "COMMUNITY"})
    void otherRolesCannotUploadHealthProofForWorkers(String role) {
        login(role, 50L, 2L);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> files.upload(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-".getBytes()),
                        "STAFF_HEALTH_CERT", 2L)).status());
        verify(db, never()).insert(anyMap());
    }
}
