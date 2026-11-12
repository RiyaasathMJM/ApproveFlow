package com.neirah.approveflow;

import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.user.UserRepository;
import com.neirah.approveflow.workflow.RequestType;
import com.neirah.approveflow.workflow.WorkflowDtos.ConditionDto;
import com.neirah.approveflow.workflow.WorkflowDtos.CreateWorkflowRequest;
import com.neirah.approveflow.workflow.WorkflowDtos.StepRequest;
import com.neirah.approveflow.workflow.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractApiTest {

    static final String PASSWORD = "Test@12345";

    private static final List<String> TABLES = List.of(
            "escalation_records", "audit_events", "approval_decisions", "requests",
            "workflow_steps", "workflow_versions", "workflow_definitions",
            "user_roles", "users");

    @Value("${local.server.port}")
    protected int port;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected WorkflowService workflows;
    @Autowired protected ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    protected record Resp(int status, Map<String, Object> json) {}

    // ---------- clean state before every test ----------

    @BeforeEach
    void resetDatabase() {
        jdbc.execute((ConnectionCallback<Object>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : TABLES) {
                    st.execute("TRUNCATE TABLE " + table);
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });

        createUser("Admin", "admin@demo.local", Role.ADMIN);
        createUser("Requester", "requester@demo.local", Role.REQUESTER);
        createUser("Manager", "manager@demo.local", Role.MANAGER);
        createUser("Manager Two", "manager2@demo.local", Role.MANAGER);
        createUser("Finance", "finance@demo.local", Role.FINANCE);
        createUser("Hybrid", "hybrid@demo.local", Role.REQUESTER, Role.MANAGER);

        workflows.create(new CreateWorkflowRequest("Expense Claim", RequestType.EXPENSE, List.of(
                new StepRequest(1, Role.MANAGER, null, 48),
                new StepRequest(2, Role.FINANCE,
                        new ConditionDto("amount", ">", BigDecimal.valueOf(10000)), 48))));
        workflows.create(new CreateWorkflowRequest("Leave Request", RequestType.LEAVE, List.of(
                new StepRequest(1, Role.MANAGER, null, 48))));
    }

    private void createUser(String name, String email, Role... roles) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRoles(new HashSet<>(Arrays.asList(roles)));
        users.save(u);
    }

    // ---------- HTTP helpers ----------

    @SuppressWarnings("unchecked")
    protected Resp call(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String text = res.body();
            Map<String, Object> json = null;
            if (text != null && text.startsWith("{")) {
                json = (Map<String, Object>) mapper.readValue(text, Map.class);
            }
            return new Resp(res.statusCode(), json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected String login(String email) {
        Resp r = call("POST", "/api/auth/login", null, Map.of("email", email, "password", PASSWORD));
        assertThat(r.status()).isEqualTo(200);
        return (String) r.json().get("token");
    }

    protected long createExpense(String token, int amount) {
        Resp r = call("POST", "/api/requests", token, Map.of(
                "type", "EXPENSE", "title", "Test expense", "payload", Map.of("amount", amount)));
        assertThat(r.status()).isEqualTo(201);
        return ((Number) r.json().get("id")).longValue();
    }

    protected Resp submit(String token, long id) {
        return call("POST", "/api/requests/" + id + "/submit", token, null);
    }

    protected long submittedExpense(String requesterToken, int amount) {
        long id = createExpense(requesterToken, amount);
        assertThat(submit(requesterToken, id).status()).isEqualTo(200);
        return id;
    }

    protected Resp decide(String token, long id, String action, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        if (comment != null) body.put("comment", comment);
        return call("POST", "/api/requests/" + id + "/decisions", token, body);
    }

    protected String errorCode(Resp r) {
        Object err = r.json() == null ? null : r.json().get("error");
        return err instanceof Map<?, ?> m ? String.valueOf(m.get("code")) : null;
    }

    // ---------- database helpers ----------

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, id);
    }

    protected int cycleOf(long id) {
        return count("SELECT `cycle` FROM requests WHERE id = ?", id);
    }

    protected Long versionIdOf(long id) {
        return jdbc.queryForObject("SELECT workflow_version_id FROM requests WHERE id = ?", Long.class, id);
    }

    protected String currentStepRole(long id) {
        return jdbc.queryForObject(
                "SELECT s.approver_role FROM requests r JOIN workflow_steps s ON s.id = r.current_step_id WHERE r.id = ?",
                String.class, id);
    }

    protected int decisionCount(long id) {
        return count("SELECT COUNT(*) FROM approval_decisions WHERE request_id = ?", id);
    }

    protected List<String> auditActions(long id) {
        return jdbc.queryForList("SELECT action FROM audit_events WHERE request_id = ? ORDER BY id",
                String.class, id);
    }
}