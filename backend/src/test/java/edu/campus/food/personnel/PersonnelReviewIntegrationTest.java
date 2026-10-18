package edu.campus.food.personnel;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.AuthService;
import edu.campus.food.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opt in after V15 migration. Temporary accounts, attachment metadata, submissions,
 * approved snapshots, and audit entries share one test transaction and are all rolled back.
 * No attachment files are written and no existing account is changed. */
@EnabledIfEnvironmentVariable(named = "RUN_DB_PERSONNEL_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
@Transactional
@Rollback
class PersonnelReviewIntegrationTest {
    @Autowired private PersonnelController personnel;
    @Autowired private PersonnelMapper db;
    @Autowired private AdminMapper admin;
    @Autowired private AuthService auth;
    @Autowired private JdbcTemplate jdbc;

    private final String marker = "hp_" + UUID.randomUUID().toString().substring(0, 8);
    private String proofId;

    @BeforeEach
    void attachRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private long canteen(String name) {
        return admin.canteens().stream().filter(row -> name.equals(row.get("name")))
                .map(row -> ((Number) row.get("id")).longValue()).findFirst().orElseThrow();
    }

    private long account(String suffix, String role, List<Long> canteens) {
        return ((Number) auth.createStaff(marker + "_" + suffix, "testOnly123", "回滚测试人员",
                role, canteens).get("id")).longValue();
    }

    private void login(String suffix) {
        var details = auth.loadUserByUsername(marker + "_" + suffix);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details.getUsername(), "", details.getAuthorities()));
    }

    private long id(Map<String, Object> value) { return ((Number) value.get("id")).longValue(); }
    private long version(Map<String, Object> value) { return ((Number) value.get("version")).longValue(); }

    private void error(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ApiException.class, action::run).status());
    }

    private Map<String, Object> fields(long canteenId, String certNo, String position) {
        Map<String, Object> body = new HashMap<>();
        body.put("canteenId", canteenId);
        body.put("realName", "本人填写姓名");
        body.put("contactPhone", "13800000000");
        body.put("gender", "UNSPECIFIED");
        body.put("positionName", position);
        body.put("healthCertNo", certNo);
        body.put("healthExpiresOn", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusYears(1).toString());
        body.put("certificatePath", "/api/files/" + proofId);
        body.put("lastTrainingOn", null);
        body.put("trainingNote", "本人填写培训说明");
        return body;
    }

    private Map<String, Object> snapshot(long ownerId) {
        return personnel.list().stream().filter(row -> ((Number) row.get("user_id")).longValue() == ownerId)
                .findFirst().orElse(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHEF", "PURCHASER", "CLEANER"})
    void employeeDraftSubmissionRejectionAndApprovalPreserveReviewedSnapshots(String role) {
        long first = canteen("第一食堂");
        long second = canteen("第二食堂");
        long employeeId = account("employee", role, List.of(first));
        account("other", "CLEANER", List.of(first));
        long managerId = account("manager", "MANAGER", List.of(first));
        account("other_manager", "MANAGER", List.of(second));
        account("inspector", "INSPECTOR", List.of());
        account("principal", "PRINCIPAL", List.of());
        proofId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO uploaded_file(id,purpose,canteen_id,owner_id,original_name,content_type,size_bytes)
                VALUES(?,'STAFF_HEALTH_CERT',?,?,'回滚验证证明.pdf','application/pdf',5)
                """, proofId, first, employeeId);

        login("employee");
        Map<String, Object> initial = personnel.createDraft(Map.of("canteenId", first, "userId", managerId));
        assertEquals(employeeId, ((Number) initial.get("user_id")).longValue(), "不能伪造草稿所有人");
        assertEquals("DRAFT", initial.get("status"));
        assertEquals(1, version(initial));
        error(HttpStatus.CONFLICT, () -> personnel.createDraft(Map.of("canteenId", first)));
        error(HttpStatus.BAD_REQUEST, () -> personnel.submit(id(initial), Map.of("version", version(initial))));
        error(HttpStatus.FORBIDDEN, () -> personnel.createDraft(Map.of("canteenId", second)));
        Map<String, Object> firstFields = fields(first, "JK001", role);
        firstFields.put("version", version(initial));
        Map<String, Object> completed = personnel.updateDraft(id(initial), firstFields);
        assertEquals(2, version(completed));

        login("other");
        error(HttpStatus.FORBIDDEN, () -> personnel.updateDraft(id(initial), firstFields));
        error(HttpStatus.FORBIDDEN, () -> personnel.submit(id(initial), Map.of("version", version(completed))));
        login("employee");
        Map<String, Object> pending = personnel.submit(id(initial), Map.of("version", version(completed)));
        assertEquals("PENDING", pending.get("status"));
        assertNotNull(pending.get("submitted_at"));
        assertNull(snapshot(employeeId));
        error(HttpStatus.CONFLICT, () -> personnel.createDraft(fields(first, "JK002", role)));

        login("inspector");
        assertNull(snapshot(employeeId), "监管不应看见未审核档案");
        error(HttpStatus.FORBIDDEN, personnel::reviews);
        login("principal");
        assertNull(snapshot(employeeId), "校长不应看见未审核档案");
        login("other_manager");
        assertTrue(personnel.reviews().stream().noneMatch(row -> id(row) == id(initial)));
        error(HttpStatus.FORBIDDEN, () -> personnel.review(id(initial), Map.of("decision", "APPROVED", "version", version(pending))));

        login("manager");
        assertTrue(personnel.reviews().stream().anyMatch(row -> id(row) == id(initial)));
        assertNull(snapshot(employeeId));
        error(HttpStatus.CONFLICT, () -> personnel.save(employeeId, first, firstFields));
        error(HttpStatus.BAD_REQUEST, () -> personnel.review(id(initial), Map.of("decision", "REJECTED", "version", version(pending), "note", " ")));
        Map<String, Object> rejected = personnel.review(id(initial), Map.of("decision", "REJECTED", "version", version(pending), "note", "请核实健康证明姓名"));
        assertEquals("REJECTED", rejected.get("status"));
        assertEquals("请核实健康证明姓名", rejected.get("review_note"));
        assertNull(snapshot(employeeId));

        login("employee");
        assertTrue(personnel.mine().stream().anyMatch(row -> "请核实健康证明姓名".equals(row.get("review_note"))));
        Map<String, Object> revised = personnel.createDraft(fields(first, "JK001", role));
        Map<String, Object> revisionPending = personnel.submit(id(revised), Map.of("version", version(revised)));
        login("manager");
        error(HttpStatus.CONFLICT, () -> personnel.review(id(revised), Map.of("decision", "APPROVED", "version", 1)));
        Map<String, Object> approved = personnel.review(id(revised), Map.of("decision", "APPROVED", "version", version(revisionPending)));
        assertEquals("APPROVED", approved.get("status"));
        assertEquals(id(revised), ((Number) snapshot(employeeId).get("approved_submission_id")).longValue());
        assertEquals(managerId, ((Number) snapshot(employeeId).get("approved_by")).longValue());
        error(HttpStatus.CONFLICT, () -> personnel.review(id(revised), Map.of("decision", "APPROVED", "version", version(revisionPending))));

        login("employee");
        Map<String, Object> renewal = personnel.createDraft(fields(first, "JK002", role));
        assertEquals("JK001", snapshot(employeeId).get("health_cert_no"), "新草稿不能覆盖通过版本");
        Map<String, Object> renewalPending = personnel.submit(id(renewal), Map.of("version", version(renewal)));
        login("inspector");
        assertEquals("JK001", snapshot(employeeId).get("health_cert_no"), "待审期间监管只能查看通过版本");
        login("manager");
        personnel.review(id(renewal), Map.of("decision", "REJECTED", "version", version(renewalPending), "note", "证件信息需要更正"));
        assertEquals("JK001", snapshot(employeeId).get("health_cert_no"), "退回不能改写原通过快照");

        login("employee");
        Map<String, Object> corrected = personnel.createDraft(fields(first, "JK003", role));
        Map<String, Object> correctedPending = personnel.submit(id(corrected), Map.of("version", version(corrected)));
        login("manager");
        personnel.review(id(corrected), Map.of("decision", "APPROVED", "version", version(correctedPending)));
        login("principal");
        assertEquals("JK003", snapshot(employeeId).get("health_cert_no"));
        assertEquals(id(corrected), ((Number) snapshot(employeeId).get("approved_submission_id")).longValue());
        assertEquals(4, db.mine(employeeId).size(), "退回和通过的提交历史均保留");
    }

    @AfterTransaction
    void temporaryAccountsFilesSubmissionsAndProfilesWereRolledBack() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username LIKE ?", Integer.class, marker + "%"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM staff_profile_submission s JOIN app_user u ON u.id=s.user_id WHERE u.username LIKE ?",
                Integer.class, marker + "%"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM staff_profile p JOIN app_user u ON u.id=p.user_id WHERE u.username LIKE ?",
                Integer.class, marker + "%"));
        if (proofId != null) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_file WHERE id=?", Integer.class, proofId));
    }
}
