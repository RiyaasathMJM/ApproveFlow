package com.neirah.approveflow;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApprovalTest extends AbstractApiTest {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // T01: 9,500 expense, Manager approval mattum
    @Test
    void t01_expenseUpTo10000_needsManagerOnly() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");

        long id = submittedExpense(requester, 9500);
        assertThat(currentStepRole(id)).isEqualTo("MANAGER");

        assertThat(decide(manager, id, "APPROVE", null).status()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(decisionCount(id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM requests WHERE id = ? AND completed_at IS NOT NULL", id))
                .isEqualTo(1);
    }

    // T02 (compulsory): 15,000 expense, Manager then Finance
    @Test
    void t02_expenseOver10000_goesManagerThenFinance() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        String finance = login("finance@demo.local");

        long id = submittedExpense(requester, 15000);

        assertThat(decide(manager, id, "APPROVE", null).status()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(currentStepRole(id)).isEqualTo("FINANCE");

        assertThat(decide(finance, id, "APPROVE", null).status()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("APPROVED");

        assertThat(decisionCount(id)).isEqualTo(2);
        assertThat(auditActions(id)).containsExactly("SUBMIT", "APPROVE", "APPROVE");
    }

    // T03: Finance, Manager-ku munnaadi decide panna try
    @Test
    void t03_financeBeforeManager_isForbiddenAndChangesNothing() {
        String requester = login("requester@demo.local");
        String finance = login("finance@demo.local");

        long id = submittedExpense(requester, 15000);
        Resp r = decide(finance, id, "APPROVE", null);

        assertThat(r.status()).isIn(403, 409);
        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(currentStepRole(id)).isEqualTo("MANAGER");
        assertThat(decisionCount(id)).isZero();
    }

    // T04 (compulsory): self-approval and requester-role approval block
    @Test
    void t04_requesterCannotApproveOwnRequest() {
        String requester = login("requester@demo.local");
        long id = submittedExpense(requester, 5000);

        // plain requester: role-ale 403
        assertThat(decide(requester, id, "APPROVE", null).status()).isEqualTo(403);

        // REQUESTER + MANAGER role irukkura user, own request-a approve panna try
        String hybrid = login("hybrid@demo.local");
        long own = submittedExpense(hybrid, 5000);
        Resp r = decide(hybrid, own, "APPROVE", null);

        assertThat(r.status()).isEqualTo(403);
        assertThat(errorCode(r)).isEqualTo("SELF_APPROVAL");

        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(statusOf(own)).isEqualTo("PENDING_APPROVAL");
        assertThat(decisionCount(id)).isZero();
        assertThat(decisionCount(own)).isZero();
    }

    // T05 (compulsory): rendu concurrent approvals, exactly one committed transition
    @Test
    void t05_twoConcurrentApprovals_commitExactlyOne() throws Exception {
        String requester = login("requester@demo.local");
        String manager1 = login("manager@demo.local");
        String manager2 = login("manager2@demo.local");

        for (int round = 0; round < 3; round++) {
            long id = submittedExpense(requester, 9500);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            Callable<Resp> first = () -> {
                ready.countDown();
                go.await();
                return decide(manager1, id, "APPROVE", null);
            };
            Callable<Resp> second = () -> {
                ready.countDown();
                go.await();
                return decide(manager2, id, "APPROVE", null);
            };

            Future<Resp> f1 = pool.submit(first);
            Future<Resp> f2 = pool.submit(second);
            ready.await();
            go.countDown();

            List<Integer> codes = List.of(f1.get().status(), f2.get().status());
            pool.shutdown();

            assertThat(codes).containsExactlyInAnyOrder(200, 409);
            assertThat(statusOf(id)).isEqualTo("APPROVED");
            assertThat(decisionCount(id)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM audit_events WHERE request_id = ? AND action = 'APPROVE'", id))
                    .isEqualTo(1);
        }
    }

    // T06: reject with empty reason
    @Test
    void t06_rejectWithEmptyReason_isValidationErrorAndRequestUnchanged() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        long id = submittedExpense(requester, 9500);

        Resp empty = decide(manager, id, "REJECT", null);
        Resp blank = decide(manager, id, "REJECT", "   ");

        assertThat(empty.status()).isEqualTo(422);
        assertThat(blank.status()).isEqualTo(422);
        assertThat(errorCode(empty)).isEqualTo("VALIDATION_ERROR");
        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(currentStepRole(id)).isEqualTo("MANAGER");
        assertThat(decisionCount(id)).isZero();
    }

    // T07 (compulsory): returned request edit + resubmit, new cycle, history preserved
    @Test
    void t07_returnedRequestResubmit_startsNewCycleAndKeepsHistory() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");

        long id = submittedExpense(requester, 15000);
        long versionBefore = versionIdOf(id);
        assertThat(cycleOf(id)).isEqualTo(1);

        assertThat(decide(manager, id, "RETURN_FOR_CHANGES", "Receipt missing").status()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("RETURNED");

        Resp edit = call("PATCH", "/api/requests/" + id, requester, Map.of(
                "title", "Test expense (receipt added)",
                "payload", Map.of("amount", 15000, "category", "MEALS")));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(submit(requester, id).status()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(cycleOf(id)).isEqualTo(2);
        assertThat(versionIdOf(id)).isEqualTo(versionBefore);
        assertThat(currentStepRole(id)).isEqualTo("MANAGER");

        // cycle 1 decision apdiye irukku
        assertThat(jdbc.queryForList(
                "SELECT CONCAT(`cycle`, ':', action) FROM approval_decisions WHERE request_id = ? ORDER BY id",
                String.class, id)).containsExactly("1:RETURN_FOR_CHANGES");
        assertThat(auditActions(id)).containsExactly("SUBMIT", "RETURN", "RESUBMIT");

        // cycle 2-la Manager thirumba decide panna mudiyum
        assertThat(decide(manager, id, "APPROVE", null).status()).isEqualTo(200);
        assertThat(jdbc.queryForList(
                "SELECT CONCAT(`cycle`, ':', action) FROM approval_decisions WHERE request_id = ? ORDER BY id",
                String.class, id)).containsExactly("1:RETURN_FOR_CHANGES", "2:APPROVE");
    }

    // T08 (compulsory): workflow edit aanaalum in-flight request original version use pannum
    @Test
    void t08_workflowEditedWhileActive_requestKeepsOriginalVersion() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        String admin = login("admin@demo.local");

        long a = submittedExpense(requester, 15000);
        Long v1 = versionIdOf(a);

        // Admin: Finance threshold 10,000 irundhu 50,000-ku maathi pudhu version
        Resp v2Resp = call("POST", "/api/workflows", admin, Map.of(
                "name", "Expense Claim",
                "requestType", "EXPENSE",
                "steps", List.of(
                        Map.of("sequence", 1, "approverRole", "MANAGER", "dueHours", 48),
                        Map.of("sequence", 2, "approverRole", "FINANCE", "dueHours", 48,
                                "condition", Map.of("field", "amount", "op", ">", "value", 50000)))));
        assertThat(v2Resp.status()).isEqualTo(201);

        Long v2 = jdbc.queryForObject(
                "SELECT current_version_id FROM workflow_definitions WHERE name = 'Expense Claim'", Long.class);
        assertThat(v2).isNotEqualTo(v1);
        assertThat(versionIdOf(a)).isEqualTo(v1);   // submitted request maaraadhu

        // A: pazhaya rule (15,000 > 10,000), Finance-ku pogum
        assertThat(decide(manager, a, "APPROVE", null).status()).isEqualTo(200);
        assertThat(statusOf(a)).isEqualTo("PENDING_APPROVAL");
        assertThat(currentStepRole(a)).isEqualTo("FINANCE");
        assertThat(versionIdOf(a)).isEqualTo(v1);

        // B: same amount, pudhu version (15,000 <= 50,000), Manager approve pannina mudinjidum
        long b = submittedExpense(requester, 15000);
        assertThat(versionIdOf(b)).isEqualTo(v2);
        assertThat(decide(manager, b, "APPROVE", null).status()).isEqualTo(200);
        assertThat(statusOf(b)).isEqualTo("APPROVED");

        // pazhaya version-oda steps maaraama irukku
        assertThat(count("SELECT COUNT(*) FROM workflow_steps WHERE version_id = ?", v1)).isEqualTo(2);
    }

    // T09: escalation rendu thadava, one event per step/cycle
    @Test
    void t09_escalationRunTwice_createsSingleRecord() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        String admin = login("admin@demo.local");

        long id = submittedExpense(requester, 9500);
        jdbc.update("UPDATE requests SET current_step_due_at = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR WHERE id = ?", id);

        Resp first = call("POST", "/api/admin/escalations/run", admin, null);
        Resp second = call("POST", "/api/admin/escalations/run", admin, null);

        assertThat(first.status()).isEqualTo(200);
        assertThat(((Number) first.json().get("newlyEscalated")).intValue()).isEqualTo(1);
        assertThat(second.status()).isEqualTo(200);
        assertThat(((Number) second.json().get("newlyEscalated")).intValue()).isZero();

        assertThat(count("SELECT COUNT(*) FROM escalation_records WHERE request_id = ?", id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_events WHERE request_id = ? AND action = 'ESCALATE'", id))
                .isEqualTo(1);

        // route maaralai
        assertThat(statusOf(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(currentStepRole(id)).isEqualTo("MANAGER");

        // overdue aanaalum approver innum approve panna mudiyum
        assertThat(decide(manager, id, "APPROVE", null).status()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    // T10: token illa / expired / thappu, safe error response
    @Test
    void t10_missingInvalidOrExpiredToken_isDeniedSafely() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        String admin = login("admin@demo.local");

        Resp none = call("GET", "/api/requests", null, null);
        assertThat(none.status()).isEqualTo(401);
        assertThat(errorCode(none)).isEqualTo("UNAUTHORIZED");

        assertThat(call("GET", "/api/requests", "not-a-real-token", null).status()).isEqualTo(401);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        String expired = Jwts.builder()
                .subject("1")
                .issuedAt(new Date(now - 7_200_000))
                .expiration(new Date(now - 3_600_000))
                .signWith(key)
                .compact();
        assertThat(call("GET", "/api/auth/me", expired, null).status()).isEqualTo(401);

        // password hash eppovum expose aagaadhu
        Resp me = call("GET", "/api/auth/me", manager, null);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json()).doesNotContainKey("passwordHash");

        // vera aal-oda draft: Manager-ku 404, Admin-ku 200
        long draft = createExpense(requester, 1000);
        assertThat(call("GET", "/api/requests/" + draft, manager, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/requests/" + draft, admin, null).status()).isEqualTo(200);
    }

    // T11: negative expense, invalid leave date range
    @Test
    void t11_invalidPayloads_areRejectedAtValidation() {
        String requester = login("requester@demo.local");

        Resp negative = call("POST", "/api/requests", requester, Map.of(
                "type", "EXPENSE", "title", "Bad expense", "payload", Map.of("amount", -500)));
        assertThat(negative.status()).isEqualTo(422);

        Resp badDates = call("POST", "/api/requests", requester, Map.of(
                "type", "LEAVE", "title", "Bad leave",
                "payload", Map.of("leaveType", "ANNUAL", "startDate", "2026-12-24",
                        "endDate", "2026-12-20", "reason", "Trip")));
        assertThat(badDates.status()).isEqualTo(422);

        assertThat(count("SELECT COUNT(*) FROM requests")).isZero();
    }

    // T12: rejected request-ku innoru decision
    @Test
    void t12_decisionOnRejectedRequest_isConflict() {
        String requester = login("requester@demo.local");
        String manager = login("manager@demo.local");
        String manager2 = login("manager2@demo.local");

        long id = submittedExpense(requester, 15000);
        assertThat(decide(manager, id, "REJECT", "Receipt is missing").status()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo("REJECTED");

        Resp again = decide(manager2, id, "APPROVE", null);

        assertThat(again.status()).isEqualTo(409);
        assertThat(statusOf(id)).isEqualTo("REJECTED");
        assertThat(decisionCount(id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM requests WHERE id = ? AND current_step_id IS NULL", id))
                .isEqualTo(1);
    }
}
