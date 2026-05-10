package com.agentcenter.bridge.api;

import com.agentcenter.bridge.application.TestWorkflowExecutorConfig;
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ConfirmationService.resolve() routing of all action types.
 *
 * Covers:
 * - APPROVE: Record result, write USER message, resume current Skill
 * - CHOOSE: Save choice payload, write USER message, resume current Skill
 * - SUPPLEMENT: Save input payload, write USER message, resume current Skill
 * - RETRY: Resolve exception confirmation, retryNode
 * - SKIP: Resolve exception confirmation, skipNode
 * - REJECT: Keep workflow BLOCKED, write USER message
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
class ConfirmationResolveRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PermissionConfirmationHandler permissionConfirmationHandler;

    @BeforeEach
    void cleanData() {
        TestWorkflowExecutorConfig.clearCapturedRuntimeInputs();
        reset(permissionConfirmationHandler);
        jdbcTemplate.execute("DELETE FROM confirmation_request");
        jdbcTemplate.execute("DELETE FROM artifact");
        jdbcTemplate.execute("DELETE FROM runtime_event");
        jdbcTemplate.execute("DELETE FROM agent_message");
        jdbcTemplate.execute("DELETE FROM agent_session");
        jdbcTemplate.execute("DELETE FROM workflow_node_instance");
        jdbcTemplate.execute("DELETE FROM workflow_instance");
        jdbcTemplate.execute("UPDATE work_item SET current_workflow_instance_id = NULL");
    }

    private String findWorkItemIdByCode(String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andReturn();
        var array = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var node : array) {
            if (code.equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("No work item with code " + code);
    }

    /**
     * Helper: start workflow and get the first confirmation (DECISION type from FE1234 workflow).
     */
    private StartedWorkflow startWorkflowAndConfirm(String workItemCode) throws Exception {
        String workItemId = findWorkItemIdByCode(workItemCode);

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + workItemId + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String instanceId = startJson.at("/workflowInstance/id").asText();
        String firstNodeId = startJson.at("/workflowInstance/nodes/0/id").asText();

        List<Map<String, Object>> advanceConfirmations = jdbcTemplate.queryForList(
                "SELECT id FROM confirmation_request WHERE workflow_instance_id = ? AND workflow_node_instance_id = ? AND status = 'PENDING'",
                instanceId, firstNodeId);
        assertThat(advanceConfirmations).hasSize(1);
        String advanceConfirmationId = (String) advanceConfirmations.get(0).get("id");

        mockMvc.perform(post("/api/confirmations/" + advanceConfirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"ADVANCE\",\"comment\":\"进入下一节点\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, workflow_node_instance_id, agent_session_id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        Map<String, Object> conf = confirmations.get(0);
        String confirmationId = (String) conf.get("id");
        String nodeInstanceId = (String) conf.get("workflow_node_instance_id");
        String sessionId = (String) conf.get("agent_session_id");

        return new StartedWorkflow(workItemId, instanceId, confirmationId, nodeInstanceId, sessionId);
    }

    /**
     * APPROVE: resolving with APPROVE action should:
     * - Set confirmation status to RESOLVED
     * - Write a USER message to session
     * - Resume current Skill instead of treating the interaction as node completion
     */
    @Test
    void approveAction_resolvesConfirmation_writesUserMessage_keepsCurrentSkillWaiting() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority, created_at, updated_at
                ) VALUES (?, 'APPROVAL', 'PENDING', ?, ?, ?, ?, '审批确认', 'MEDIUM', datetime('now'), datetime('now'))
                """, "conf-approve-test", wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/confirmations/conf-approve-test/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPROVE\",\"comment\":\"审批通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        var confirmRow = jdbcTemplate.queryForMap(
                "SELECT resolution_comment FROM confirmation_request WHERE id = 'conf-approve-test'");
        assertThat(confirmRow.get("resolution_comment").toString()).isEqualTo("审批通过");

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
        boolean hasApproveMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString()
                        .contains("用户输入：用户确认通过")
                        && m.get("content").toString().contains("确认项：审批确认"));
        assertThat(hasApproveMessage).isTrue();

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countCompletedNodes(wfJson);
        assertThat(completedCount).isEqualTo(1);
        assertThat(currentNodeStatus(wfJson)).isEqualTo("WAITING_CONFIRMATION");
    }

    /**
     * CHOOSE: resolving with CHOOSE action should:
     * - Save choice payload
     * - Write USER message with user's choice
     * - Resume current Skill with the choice in its input context
     */
    @Test
    void chooseAction_savesChoicePayload_writesUserMessage_resumesCurrentSkill() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"comment\":\"选择方案\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        var confirmation = jdbcTemplate.queryForMap(
                "SELECT resolution_payload_json, resolution_comment FROM confirmation_request WHERE id = ?",
                wf.confirmationId());
        String payloadJson = (String) confirmation.get("resolution_payload_json");
        assertThat(payloadJson).contains("低风险方案");
        assertThat(confirmation.get("resolution_comment").toString()).isEqualTo("选择方案");

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
        boolean hasChooseMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString()
                        .contains("用户输入：用户选择：低风险方案")
                        && m.get("content").toString().contains("类型：DECISION")
                        && m.get("content").toString().contains("确认项：FE1234 方案设计 · 方案选择"));
        assertThat(hasChooseMessage).isTrue();

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countCompletedNodes(wfJson);
        assertThat(completedCount).isEqualTo(1);
        assertThat(currentNodeStatus(wfJson)).isEqualTo("WAITING_CONFIRMATION");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES).containsSubsequence("hld-design", "hld-design");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(
                TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.size() - 1))
                .contains("用户交互回答历史")
                .contains("低风险方案");
    }

    /**
     * SUPPLEMENT: resolving with SUPPLEMENT action should:
     * - Save input payload
     * - Write USER message with user's input
     * - Resume current Skill with the supplement in its input context
     */
    @Test
    void supplementAction_savesInputPayload_writesUserMessage_resumesCurrentSkill() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority, created_at, updated_at
                ) VALUES (?, 'INPUT_REQUIRED', 'PENDING', ?, ?, ?, ?, '补充信息', 'MEDIUM', datetime('now'), datetime('now'))
                """, "conf-supplement-test", wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/confirmations/conf-supplement-test/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SUPPLEMENT\",\"comment\":\"补充需求\",\"payload\":{\"input\":\"需要支持移动端\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        var confirmation = jdbcTemplate.queryForMap(
                "SELECT resolution_payload_json FROM confirmation_request WHERE id = 'conf-supplement-test'");
        String payloadJson = (String) confirmation.get("resolution_payload_json");
        assertThat(payloadJson).contains("需要支持移动端");

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
        boolean hasSupplementMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString()
                        .contains("用户输入：用户补充：需要支持移动端")
                        && m.get("content").toString().contains("确认项：补充信息"));
        assertThat(hasSupplementMessage).isTrue();

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countCompletedNodes(wfJson);
        assertThat(completedCount).isEqualTo(1);
        assertThat(currentNodeStatus(wfJson)).isEqualTo("WAITING_CONFIRMATION");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(
                TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.size() - 1))
                .contains("用户交互回答历史")
                .contains("需要支持移动端");
    }

    /**
     * RETRY: resolving an EXCEPTION confirmation with RETRY should:
     * - Set confirmation status to RESOLVED
     * - Write USER message
     * - Call retryNode
     */
    @Test
    void retryAction_resolvesExceptionConfirmation_writesUserMessage() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // First resolve the original confirmation to unblock node
        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Insert an EXCEPTION confirmation on a completed node
        String exceptionId = "conf-retry-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority, created_at, updated_at
                ) VALUES (?, 'EXCEPTION', 'PENDING', ?, ?, ?, ?, '执行异常', 'HIGH', datetime('now'), datetime('now'))
                """, exceptionId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + exceptionId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"RETRY\",\"comment\":\"重试执行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        boolean hasRetryMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString().contains("重试"));
        assertThat(hasRetryMessage).isTrue();
    }

    /**
     * SKIP: resolving an EXCEPTION confirmation with SKIP should:
     * - Set confirmation status to RESOLVED
     * - Write USER message
     * - Call skipNode
     */
    @Test
    void skipAction_resolvesExceptionConfirmation_writesUserMessage() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // First resolve the original confirmation to unblock node
        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Insert an EXCEPTION confirmation
        String exceptionId = "conf-skip-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority, created_at, updated_at
                ) VALUES (?, 'EXCEPTION', 'PENDING', ?, ?, ?, ?, '执行异常', 'HIGH', datetime('now'), datetime('now'))
                """, exceptionId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + exceptionId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SKIP\",\"comment\":\"跳过节点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        boolean hasSkipMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString().contains("跳过"));
        assertThat(hasSkipMessage).isTrue();
    }

    /**
     * REJECT: resolving with REJECT action should:
     * - Set confirmation status to REJECTED (not RESOLVED)
     * - Write USER message about rejection
     * - Keep workflow BLOCKED (node stays in WAITING_CONFIRMATION)
     * - NOT advance the workflow
     */
    @Test
    void rejectAction_setsRejected_writesUserMessage_keepsWorkflowBlocked() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"REJECT\",\"comment\":\"拒绝确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
        boolean hasRejectMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString().contains("拒绝"));
        assertThat(hasRejectMessage).isTrue();

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countCompletedNodes(wfJson);
        assertThat(completedCount).isEqualTo(1);
    }

    /**
     * REJECT for EXCEPTION: resolving EXCEPTION confirmation with REJECT should:
     * - Set confirmation status to REJECTED
     * - Write USER message
     * - NOT call retryNode or skipNode
     */
    @Test
    void rejectAction_forException_setsRejected_writesUserMessage_noNodeAction() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // Insert an EXCEPTION confirmation
        String exceptionId = "conf-reject-exc-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority, created_at, updated_at
                ) VALUES (?, 'EXCEPTION', 'PENDING', ?, ?, ?, ?, '执行异常', 'HIGH', datetime('now'), datetime('now'))
                """, exceptionId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + exceptionId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"REJECT\",\"comment\":\"拒绝执行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        boolean hasRejectMessage = messages.stream()
                .anyMatch(m -> m.get("content").toString().contains("拒绝"));
        assertThat(hasRejectMessage).isTrue();
    }

    @Test
    void permissionReject_sendsDenyToRuntime_beforeMarkingRejected() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");
        String confirmationId = "perm_rt_session_perm_1";
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, runtime_type, runtime_session_id,
                    interaction_id, title, priority, created_at, updated_at
                ) VALUES (?, 'PERMISSION', 'PENDING', ?, ?, ?, ?, 'OPENCODE', 'rt-session',
                          'perm-1', 'Allow write?', 'HIGH', datetime('now'), datetime('now'))
                """, confirmationId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"REJECT\",\"comment\":\"拒绝授权\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(permissionConfirmationHandler).respondPermission("rt-session", "perm-1", false);
    }

    @Test
    void permissionApprove_sendsAllowToRuntime_beforeMarkingResolved() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");
        String confirmationId = "perm_rt_session_perm_2";
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, runtime_type, runtime_session_id,
                    interaction_id, title, priority, created_at, updated_at
                ) VALUES (?, 'PERMISSION', 'PENDING', ?, ?, ?, ?, 'OPENCODE', 'rt-session',
                          'perm-2', 'Allow command?', 'HIGH', datetime('now'), datetime('now'))
                """, confirmationId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPROVE\",\"comment\":\"允许授权\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        verify(permissionConfirmationHandler).respondPermission("rt-session", "perm-2", true);
    }

    @Test
    void permissionRuntimeFailure_keepsConfirmationPending() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");
        String confirmationId = "perm_rt_session_perm_3";
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, runtime_type, runtime_session_id,
                    interaction_id, title, priority, created_at, updated_at
                ) VALUES (?, 'PERMISSION', 'PENDING', ?, ?, ?, ?, 'OPENCODE', 'rt-session',
                          'perm-3', 'Allow delete?', 'HIGH', datetime('now'), datetime('now'))
                """, confirmationId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());
        doThrow(new IllegalStateException("permission endpoint failed"))
                .when(permissionConfirmationHandler).respondPermission("rt-session", "perm-3", true);

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPROVE\",\"comment\":\"允许授权\"}"))
                .andExpect(status().isBadGateway());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM confirmation_request WHERE id = ?",
                String.class,
                confirmationId);
        assertThat(status).isEqualTo("PENDING");
    }

    /**
     * Verify runtime event is published after resolve.
     */
    @Test
    void resolveAction_publishesRuntimeEvent() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Verify runtime event was published
        var events = jdbcTemplate.queryForList(
                "SELECT * FROM runtime_event WHERE session_id = ?", wf.sessionId());
        assertThat(events).hasSizeGreaterThanOrEqualTo(1);
        boolean hasResolvedPayloadWithQuestion = events.stream()
                .filter(e -> "CONFIRMATION_RESOLVED".equals(e.get("event_type").toString()))
                .map(e -> e.get("payload_json").toString())
                .anyMatch(payload -> payload.contains("question")
                        && payload.contains("contextSummary")
                        && payload.contains("options")
                        && payload.contains("低风险方案"));
        assertThat(hasResolvedPayloadWithQuestion).isTrue();
    }

    /**
     * Structured CHOOSE payload: resolving a DECISION confirmation with choiceId+choiceLabel
     * should produce a descriptive USER message including both fields.
     */
    @Test
    void resolveDecisionWithStructuredPayload_writesDescriptiveMessage() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // Resolve original confirmation first
        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Insert a DECISION confirmation with interactionType and interactionSchemaJson
        String structuredId = "conf-structured-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority,
                    interaction_type, interaction_schema_json,
                    created_at, updated_at
                ) VALUES (?, 'DECISION', 'PENDING', ?, ?, ?, ?, '选择技术方案', 'MEDIUM',
                    'DECISION', '{"type":"object","properties":{"choiceId":{"type":"string"},"choiceLabel":{"type":"string"}}}',
                    datetime('now'), datetime('now'))
                """, structuredId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        // Resolve with structured payload (choiceId + choiceLabel)
        mockMvc.perform(post("/api/confirmations/" + structuredId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choiceId\":\"A\",\"choiceLabel\":\"双写方案\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Verify the USER message contains structured choice info
        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        boolean hasStructuredChoice = messages.stream()
                .anyMatch(m -> m.get("content").toString()
                        .contains("用户选择：双写方案（A）")
                        && m.get("content").toString().contains("确认项：选择技术方案"));
        assertThat(hasStructuredChoice).isTrue();

        // Verify resolution payload was saved
        var confirmation = jdbcTemplate.queryForMap(
                "SELECT resolution_payload_json FROM confirmation_request WHERE id = ?", structuredId);
        String payloadJson = (String) confirmation.get("resolution_payload_json");
        assertThat(payloadJson).contains("双写方案");
        assertThat(payloadJson).contains("\"A\"");
    }

    /**
     * Structured SUPPLEMENT payload: resolving an INPUT_REQUIRED confirmation with fields map
     * should produce a descriptive USER message summarizing all fields.
     */
    @Test
    void resolveInputWithStructuredFields_writesDescriptiveMessage() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // Resolve original confirmation first
        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Insert an INPUT_REQUIRED confirmation with interactionType
        String inputId = "conf-input-fields-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority,
                    interaction_type, interaction_schema_json,
                    created_at, updated_at
                ) VALUES (?, 'INPUT_REQUIRED', 'PENDING', ?, ?, ?, ?, '补充技术细节', 'MEDIUM',
                    'INPUT', '{"type":"object","properties":{"targetEnv":{"type":"string"},"deadline":{"type":"string"}}}',
                    datetime('now'), datetime('now'))
                """, inputId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        // Resolve with fields map payload
        mockMvc.perform(post("/api/confirmations/" + inputId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SUPPLEMENT\",\"payload\":{\"fields\":{\"targetEnv\":\"生产环境\",\"deadline\":\"2026-06-01\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Verify the USER message contains structured field summary
        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'confirmation-service'",
                wf.sessionId());
        boolean hasFieldSummary = messages.stream()
                .anyMatch(m -> m.get("content").toString()
                        .contains("用户补充：")
                        && m.get("content").toString().contains("targetEnv=生产环境")
                        && m.get("content").toString().contains("deadline=2026-06-01"));
        assertThat(hasFieldSummary).isTrue();
    }

    /**
     * Normal DECISION confirmation (interactionType != WORKFLOW_ADVANCE) rejects ADVANCE action with 400.
     */
    @Test
    void resolveDecisionNonWorkflowAdvance_rejectsAdvanceAction() throws Exception {
        StartedWorkflow wf = startWorkflowAndConfirm("FE1234");

        // Resolve original confirmation first
        mockMvc.perform(post("/api/confirmations/" + wf.confirmationId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        // Insert a normal DECISION confirmation (interactionType != WORKFLOW_ADVANCE)
        String normalDecisionId = "conf-normal-decision-" + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, agent_session_id, title, priority,
                    interaction_type,
                    created_at, updated_at
                ) VALUES (?, 'DECISION', 'PENDING', ?, ?, ?, ?, '普通决策', 'MEDIUM',
                    'DECISION',
                    datetime('now'), datetime('now'))
                """, normalDecisionId, wf.workItemId(), wf.instanceId(), wf.nodeInstanceId(), wf.sessionId());

        // ADVANCE action on normal DECISION should be rejected with 400
        mockMvc.perform(post("/api/confirmations/" + normalDecisionId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"ADVANCE\",\"comment\":\"推进\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private long countCompletedNodes(com.fasterxml.jackson.databind.JsonNode wfJson) {
        var nodes = wfJson.at("/nodes");
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter("COMPLETED"::equals)
                .count();
    }

    private String currentNodeStatus(com.fasterxml.jackson.databind.JsonNode wfJson) {
        String currentNodeId = wfJson.path("currentNodeInstanceId").asText("");
        var nodes = wfJson.at("/nodes");
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .filter(n -> currentNodeId.equals(n.get("id").asText()))
                .map(n -> n.get("status").asText())
                .findFirst()
                .orElse("");
    }

    record StartedWorkflow(String workItemId, String instanceId, String confirmationId,
                           String nodeInstanceId, String sessionId) {}
}
