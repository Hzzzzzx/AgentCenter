package com.agentcenter.bridge.api;

import com.agentcenter.bridge.application.TestWorkflowExecutorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ConfirmationService.resolve() routing of all action types.
 *
 * Covers:
 * - APPROVE: Record result, write USER message, complete node, advance
 * - CHOOSE: Save choice payload, write USER message, complete node, advance
 * - SUPPLEMENT: Save input payload, write USER message, complete node, advance
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

    @BeforeEach
    void cleanData() {
        TestWorkflowExecutorConfig.clearCapturedRuntimeInputs();
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
                .andExpect(jsonPath("$.confirmation").exists())
                .andExpect(jsonPath("$.confirmation.id").isNotEmpty())
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String instanceId = startJson.at("/workflowInstance/id").asText();
        String confirmationId = startJson.at("/confirmation/id").asText();
        String nodeInstanceId = startJson.at("/confirmation/workflowNodeInstanceId").asText();
        String sessionId = startJson.at("/confirmation/agentSessionId").asText();

        return new StartedWorkflow(workItemId, instanceId, confirmationId, nodeInstanceId, sessionId);
    }

    /**
     * APPROVE: resolving with APPROVE action should:
     * - Set confirmation status to RESOLVED
     * - Write a USER message to session
     * - Complete node and advance workflow
     */
    @Test
    void approveAction_resolvesConfirmation_writesUserMessage_advancesWorkflow() throws Exception {
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
        assertThat(completedCount).isGreaterThanOrEqualTo(2);
    }

    /**
     * CHOOSE: resolving with CHOOSE action should:
     * - Save choice payload
     * - Write USER message with user's choice
     * - Complete node and advance workflow
     */
    @Test
    void chooseAction_savesChoicePayload_writesUserMessage_advancesWorkflow() throws Exception {
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
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("content").toString())
                .contains("用户输入：用户选择：低风险方案")
                .contains("类型：DECISION");

        // Verify workflow advanced
        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countCompletedNodes(wfJson);
        assertThat(completedCount).isGreaterThanOrEqualTo(2);
    }

    /**
     * SUPPLEMENT: resolving with SUPPLEMENT action should:
     * - Save input payload
     * - Write USER message with user's input
     * - Complete node and advance workflow
     */
    @Test
    void supplementAction_savesInputPayload_writesUserMessage_advancesWorkflow() throws Exception {
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
        assertThat(completedCount).isGreaterThanOrEqualTo(2);
    }

    /**
     * RETRY: resolving an EXCEPTION confirmation with RETRY should:
     * - Set confirmation status to RESOLVED
     * - Write USER message
     * - Call retryNode (not completeNodeAndScheduleAdvance)
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
     * - Call skipNode (not completeNodeAndScheduleAdvance)
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

    // --- Helpers ---

    private long countCompletedNodes(com.fasterxml.jackson.databind.JsonNode wfJson) {
        var nodes = wfJson.at("/nodes");
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter("COMPLETED"::equals)
                .count();
    }

    record StartedWorkflow(String workItemId, String instanceId, String confirmationId,
                           String nodeInstanceId, String sessionId) {}
}
