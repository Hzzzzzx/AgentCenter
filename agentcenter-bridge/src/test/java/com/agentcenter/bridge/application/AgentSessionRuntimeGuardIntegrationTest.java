package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.agentcenter.bridge.api.dto.AgentSessionDto;
import com.agentcenter.bridge.api.dto.SendMessageRequest;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.SessionType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for Runtime Guard failure chain in AgentSessionService.
 *
 * Covers:
 * - Retry exhaustion with recovery confirmation creation
 * - Deduplication of pending exception confirmations on repeated failures
 * - Workflow instance blocking when runtime failure occurs in workflow-bound session
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
@TestPropertySource(properties = {
    "agentcenter.runtime.guard.retry-limit=1",
    "agentcenter.runtime.guard.retry-backoff-ms=0"
})
class AgentSessionRuntimeGuardIntegrationTest {

    @Autowired
    AgentSessionService agentSessionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    RuntimeGateway runtimeGateway;

    @BeforeEach
    void cleanData() {
        TestWorkflowExecutorConfig.clearCapturedRuntimeInputs();
        reset(runtimeGateway);
        jdbcTemplate.execute("DELETE FROM confirmation_action");
        jdbcTemplate.execute("DELETE FROM confirmation_request");
        jdbcTemplate.execute("DELETE FROM agent_message");
        jdbcTemplate.execute("DELETE FROM agent_session");
        jdbcTemplate.execute("DELETE FROM runtime_event");
        jdbcTemplate.execute("DELETE FROM artifact");
        jdbcTemplate.execute("DELETE FROM workflow_node_instance");
        jdbcTemplate.execute("DELETE FROM workflow_instance");
        jdbcTemplate.execute("UPDATE work_item SET current_workflow_instance_id = NULL");
    }

    @Test
    void runtimeFailureExhaustsRetriesAndCreatesRecoveryConfirmation() {
        var session = agentSessionService.createSession(
                SessionType.GENERAL, "test", null, null, RuntimeType.OPENCODE);
        String sessionId = session.id();

        // ensureSession returns existing runtimeSessionId if present, otherwise creates "rt-ses-1"
        when(runtimeGateway.ensureSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    String existing = invocation.getArgument(3);
                    return existing != null ? existing : "rt-ses-1";
                });
        doThrow(new IllegalStateException("HTTP 503 Service Unavailable: timeout"))
                .when(runtimeGateway).sendMessage(eq(RuntimeType.OPENCODE), anyString(), anyString());
        when(runtimeGateway.createSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId)))
                .thenReturn("rt-recovered-1");

        agentSessionService.sendMessage(sessionId,
                new SendMessageRequest("original user message", ContentFormat.TEXT, null, null));

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
            // 1. agent_message: 2 rows — USER then ASSISTANT error
            var messages = jdbcTemplate.queryForList(
                    "SELECT role, content, seq_no FROM agent_message WHERE session_id = ? ORDER BY seq_no",
                    sessionId);
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0))
                    .containsEntry("role", "USER")
                    .containsEntry("content", "original user message");
            assertThat((String) messages.get(1).get("content"))
                    .contains("Runtime 调用失败")
                    .contains("HTTP 503");
            assertThat(messages.get(1)).containsEntry("role", "ASSISTANT");

            // 2. confirmation_request: 1 row PENDING EXCEPTION
            var confirmations = jdbcTemplate.queryForList(
                    "SELECT * FROM confirmation_request WHERE agent_session_id = ? AND status = 'PENDING'",
                    sessionId);
            assertThat(confirmations).hasSize(1);
            var c = confirmations.get(0);
            assertThat(c).containsEntry("request_type", "EXCEPTION");
            assertThat(c).containsEntry("status", "PENDING");
            assertThat(c).containsEntry("interaction_type", "RUNTIME_EXCEPTION");
            assertThat(c).containsEntry("priority", "HIGH");
            assertThat(c).containsEntry("runtime_type", "OPENCODE");
            assertThat((String) c.get("runtime_session_id")).isEqualTo("rt-recovered-1");

            // 3. optionsJson contains all four action options
            String optionsJson = (String) c.get("options_json");
            assertThat(optionsJson).contains("SUPPLEMENT").contains("RETRY").contains("SKIP").contains("REJECT");

            // 4. interactionContextJson fields
            String ctxJson = (String) c.get("interaction_context_json");
            var ctx = objectMapper.readTree(ctxJson);
            assertThat(ctx.get("retryCount").asInt()).isEqualTo(1);
            assertThat(ctx.get("recoveryMode").asText()).isEqualTo("SAFE_AUTO_EXHAUSTED");
            assertThat(ctx.get("failureCategory").asText()).isEqualTo("TRANSPORT_OR_RUNTIME");
            assertThat(ctx.get("retryPolicy").asText()).isEqualTo("USER_CONFIRM");
            assertThat(ctx.get("originalUserMessage").asText()).isEqualTo("original user message");
            assertThat(ctx.get("errorMessage").asText()).contains("HTTP 503");

            // 5. Verify mock interactions
            verify(runtimeGateway, times(2)).sendMessage(eq(RuntimeType.OPENCODE), anyString(), anyString());
            verify(runtimeGateway, times(1)).createSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId));
        });
    }

    @Test
    void duplicateRuntimeFailureDeduplicatesConfirmation() {
        var session = agentSessionService.createSession(
                SessionType.GENERAL, "test", null, null, RuntimeType.OPENCODE);
        String sessionId = session.id();

        // Phase 1: first failure
        when(runtimeGateway.ensureSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    String existing = invocation.getArgument(3);
                    return existing != null ? existing : "rt-ses-1";
                });
        doThrow(new IllegalStateException("HTTP 503 Service Unavailable: timeout"))
                .when(runtimeGateway).sendMessage(eq(RuntimeType.OPENCODE), anyString(), anyString());
        when(runtimeGateway.createSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId)))
                .thenReturn("rt-recovered-1");

        agentSessionService.sendMessage(sessionId,
                new SendMessageRequest("original user message", ContentFormat.TEXT, null, null));

        // Await first confirmation
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
            var confirmations = jdbcTemplate.queryForList(
                    "SELECT id FROM confirmation_request WHERE agent_session_id = ? AND status = 'PENDING' AND request_type = 'EXCEPTION'",
                    sessionId);
            assertThat(confirmations).hasSize(1);
        });

        String firstConfirmationId = jdbcTemplate.queryForObject(
                "SELECT id FROM confirmation_request WHERE agent_session_id = ? AND status = 'PENDING' AND request_type = 'EXCEPTION'",
                String.class, sessionId);

        // Phase 2: second failure with different error
        reset(runtimeGateway);
        when(runtimeGateway.ensureSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    String existing = invocation.getArgument(3);
                    return existing != null ? existing : "rt-ses-2";
                });
        doThrow(new IllegalStateException("HTTP 504 Gateway Timeout: second failure"))
                .when(runtimeGateway).sendMessage(eq(RuntimeType.OPENCODE), anyString(), anyString());
        when(runtimeGateway.createSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId)))
                .thenReturn("rt-recovered-2");

        agentSessionService.sendMessage(sessionId,
                new SendMessageRequest("second user message", ContentFormat.TEXT, null, null));

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
            // Still only 1 pending confirmation (deduplication)
            var confirmations = jdbcTemplate.queryForList(
                    "SELECT * FROM confirmation_request WHERE agent_session_id = ? AND status = 'PENDING' AND request_type = 'EXCEPTION'",
                    sessionId);
            assertThat(confirmations).hasSize(1);

            var c = confirmations.get(0);
            // ID preserved (deduplication)
            assertThat(c.get("id")).isEqualTo(firstConfirmationId);

            // Context updated to second failure
            String ctxJson = (String) c.get("interaction_context_json");
            var ctx = objectMapper.readTree(ctxJson);
            assertThat(ctx.get("originalUserMessage").asText()).isEqualTo("second user message");
            assertThat(ctx.get("errorMessage").asText()).contains("HTTP 504");

            // runtime_session_id updated
            assertThat((String) c.get("runtime_session_id")).isEqualTo("rt-recovered-2");

            // Messages: 4 total (USER, ASSISTANT, USER, ASSISTANT)
            var messages = jdbcTemplate.queryForList(
                    "SELECT role FROM agent_message WHERE session_id = ? ORDER BY seq_no",
                    sessionId);
            assertThat(messages).hasSize(4);
            assertThat(messages.get(0)).containsEntry("role", "USER");
            assertThat(messages.get(1)).containsEntry("role", "ASSISTANT");
            assertThat(messages.get(2)).containsEntry("role", "USER");
            assertThat(messages.get(3)).containsEntry("role", "ASSISTANT");
        });
    }

    @Test
    void runtimeFailureWithWorkflowBoundSession_blocksWorkflowInstance() {
        // Get FE1234 work item id
        String fe1234Id = findWorkItemIdByCode("FE1234");

        String definitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_definition WHERE work_item_type = 'FE' AND is_default = 1 LIMIT 1",
                String.class);

        // Insert workflow_instance with RUNNING status, NO current_node_instance_id
        String workflowInstanceId = "wf-rt-guard-test-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO workflow_instance (id, work_item_id, workflow_definition_id, status, execution_mode, started_at)
                VALUES (?, ?, ?, 'RUNNING', 'MANUAL_CONFIRM', datetime('now'))
                """, workflowInstanceId, fe1234Id, definitionId);

        // Update work item to point to this workflow instance
        jdbcTemplate.update("UPDATE work_item SET current_workflow_instance_id = ? WHERE id = ?",
                workflowInstanceId, fe1234Id);

        // Create session bound to workflow
        var session = agentSessionService.createSession(
                SessionType.WORK_ITEM, "workflow test", fe1234Id, workflowInstanceId, RuntimeType.OPENCODE);
        String sessionId = session.id();

        // Mock same retry exhaustion pattern
        when(runtimeGateway.ensureSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    String existing = invocation.getArgument(3);
                    return existing != null ? existing : "rt-ses-1";
                });
        doThrow(new IllegalStateException("HTTP 503 Service Unavailable: timeout"))
                .when(runtimeGateway).sendMessage(eq(RuntimeType.OPENCODE), anyString(), anyString());
        when(runtimeGateway.createSession(eq(RuntimeType.OPENCODE), any(), eq(sessionId)))
                .thenReturn("rt-recovered-1");

        agentSessionService.sendMessage(sessionId,
                new SendMessageRequest("workflow runtime message", ContentFormat.TEXT, null, null));

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
            // Workflow instance status = BLOCKED
            String wfStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM workflow_instance WHERE id = ?", String.class, workflowInstanceId);
            assertThat(wfStatus).isEqualTo("BLOCKED");

            // Confirmation exists with workflow binding
            var confirmations = jdbcTemplate.queryForList(
                    "SELECT * FROM confirmation_request WHERE agent_session_id = ?", sessionId);
            assertThat(confirmations).hasSize(1);
            var c = confirmations.get(0);
            assertThat(c).containsEntry("request_type", "EXCEPTION");
            assertThat(c).containsEntry("status", "PENDING");
            assertThat((String) c.get("workflow_instance_id")).isEqualTo(workflowInstanceId);

            // Context includes workflow message
            String ctxJson = (String) c.get("interaction_context_json");
            var ctx = objectMapper.readTree(ctxJson);
            assertThat(ctx.get("originalUserMessage").asText()).isEqualTo("workflow runtime message");
        });
    }

    private String findWorkItemIdByCode(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM work_item WHERE code = ?", String.class, code);
    }
}
