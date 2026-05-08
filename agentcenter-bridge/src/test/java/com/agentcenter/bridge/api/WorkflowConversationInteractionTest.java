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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for workflow conversation interaction gaps.
 * Covers:
 * - B1: Node skill output persisted as ASSISTANT message (deduplicated)
 * - B2: requiredConfirmation=true auto-creates APPROVAL when no interaction points triggered
 * - B3: Skill failure creates EXCEPTION confirmation with retry/skip options
 * - B5: DB writes happen BEFORE runtime event publishing
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
class WorkflowConversationInteractionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWorkflowData() {
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

    // ========================================================================
    // B1: Node skill output as ASSISTANT message (deduplicated)
    // ========================================================================

    @Test
    void skillOutput_isPersistedAsAssistantMessage() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Find the first completed node (requirement_refine / prd-desingn)
        var firstNode = json.at("/workflowInstance/nodes/0");
        assertThat(firstNode.get("status").asText()).isEqualTo("COMPLETED");
        String sessionId = firstNode.get("agentSessionId").asText();
        assertThat(sessionId).isNotEmpty();

        // Verify ASSISTANT messages exist for this session
        List<Map<String, Object>> assistantMessages = jdbcTemplate.queryForList(
                "SELECT id, role, content, content_format, status FROM agent_message " +
                        "WHERE session_id = ? AND role = 'ASSISTANT'", sessionId);

        assertThat(assistantMessages).isNotEmpty();

        Map<String, Object> assistantMsg = assistantMessages.get(0);
        assertThat(assistantMsg.get("role")).isEqualTo("ASSISTANT");
        assertThat(assistantMsg.get("content_format")).isEqualTo("MARKDOWN");
        assertThat(assistantMsg.get("status")).isEqualTo("COMPLETED");
        String content = (String) assistantMsg.get("content");
        assertThat(content).contains("PRD");
    }

    @Test
    void skillOutput_assistantMessage_isDeduplicated() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = json.at("/workflowInstance/nodes/0/agentSessionId").asText();

        // There should be at most 1 ASSISTANT message per node execution
        // (deduplication prevents double-insert if runtime projector also fires)
        List<Map<String, Object>> assistantMessages = jdbcTemplate.queryForList(
                "SELECT id, content FROM agent_message WHERE session_id = ? AND role = 'ASSISTANT' " +
                        "ORDER BY seq_no ASC", sessionId);

        // Check no exact duplicates in content
        long distinctContentCount = assistantMessages.stream()
                .map(m -> m.get("content"))
                .distinct()
                .count();
        assertThat(distinctContentCount).isEqualTo(assistantMessages.size());
    }

    // ========================================================================
    // B2: requiredConfirmation=true fallback creates APPROVAL when no
    //     interaction points triggered
    // ========================================================================

    @Test
    void requiredConfirmationNode_withNoTriggeredInteractions_createsApprovalConfirmation() throws Exception {
        TestWorkflowExecutorConfig.setSkillOutputForName("hld-design", """
                # HLD Output

                This is a simple HLD document with no triggered interactions.

                | 交互点 | 类型 | 是否触发 | 选项 | 触发条件 | 建议问题/动作 | 默认处理 |
                | --- | --- | --- | --- | --- | --- | --- |
                | 方案选择 | DECISION_REQUIRED | 否 | 低风险 / 高风险 | 无 | 无 | 自动 |
                """);

        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();

        var secondNode = json.at("/workflowInstance/nodes/1");
        assertThat(secondNode.get("status").asText()).isEqualTo("WAITING_CONFIRMATION");

        String secondNodeId = secondNode.get("id").asText();
        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, request_type, status, workflow_node_instance_id FROM confirmation_request " +
                        "WHERE workflow_instance_id = ? AND workflow_node_instance_id = ?",
                instanceId, secondNodeId);

        assertThat(confirmations).hasSize(1);
        Map<String, Object> conf = confirmations.get(0);
        assertThat(conf.get("request_type")).isEqualTo("APPROVAL");
        assertThat(conf.get("status")).isEqualTo("PENDING");

        assertThat(json.at("/workflowInstance/status").asText()).isIn("RUNNING", "BLOCKED");

        TestWorkflowExecutorConfig.clearSkillOutputOverrides();
    }

    // ========================================================================
    // B3: Skill failure creates EXCEPTION confirmation with retry/skip
    // ========================================================================

    @Test
    void skillFailure_createsExceptionConfirmation_withRetrySkipOptions() throws Exception {
        TestWorkflowExecutorConfig.setNextSkillResult(
                new com.agentcenter.bridge.application.runtime.SkillRunResult(
                        false, null, null, "Skill execution failed: timeout", false));

        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();

        // First node should be FAILED
        var firstNode = json.at("/workflowInstance/nodes/0");
        assertThat(firstNode.get("status").asText()).isEqualTo("FAILED");

        // Verify EXCEPTION confirmation created
        String firstNodeId = firstNode.get("id").asText();
        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, request_type, status, content, options_json FROM confirmation_request " +
                        "WHERE workflow_instance_id = ? AND workflow_node_instance_id = ?",
                instanceId, firstNodeId);

        assertThat(confirmations).hasSize(1);
        Map<String, Object> conf = confirmations.get(0);
        assertThat(conf.get("request_type")).isEqualTo("EXCEPTION");
        assertThat(conf.get("status")).isEqualTo("PENDING");

        String content = (String) conf.get("content");
        assertThat(content).contains("timeout");

        String optionsJson = (String) conf.get("options_json");
        assertThat(optionsJson).contains("重试");
        assertThat(optionsJson).contains("跳过");

        // Node status = FAILED, workflow status = BLOCKED
        assertThat(json.at("/workflowInstance/status").asText()).isEqualTo("BLOCKED");

        TestWorkflowExecutorConfig.clearCustomSkillResult();
    }

    // ========================================================================
    // B5: DB writes happen BEFORE runtime event publishing
    // ========================================================================

    @Test
    void dbWrites_happenBeforeEventPublishing_eventsReferenceExistingData() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();
        String firstNodeId = json.at("/workflowInstance/nodes/0/id").asText();
        String sessionId = json.at("/workflowInstance/nodes/0/agentSessionId").asText();

        // Verify all runtime events reference data that exists in DB
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT id, event_type, session_id, workflow_instance_id, workflow_node_instance_id FROM runtime_event " +
                        "WHERE workflow_instance_id = ?", instanceId);

        assertThat(events).isNotEmpty();

        for (Map<String, Object> event : events) {
            String eventSessionId = (String) event.get("session_id");
            String eventNodeId = (String) event.get("workflow_node_instance_id");

            // The session referenced by the event must exist
            if (eventSessionId != null) {
                int sessionCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM agent_session WHERE id = ?", Integer.class, eventSessionId);
                assertThat(sessionCount).as("Event references non-existent session: " + eventSessionId).isGreaterThan(0);
            }

            // The node referenced by the event must exist
            if (eventNodeId != null) {
                int nodeCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM workflow_node_instance WHERE id = ?", Integer.class, eventNodeId);
                assertThat(nodeCount).as("Event references non-existent node: " + eventNodeId).isGreaterThan(0);
            }
        }

        // Verify SKILL_COMPLETED event comes after artifact was created (DB write before event)
        List<Map<String, Object>> completedEvents = events.stream()
                .filter(e -> "SKILL_COMPLETED".equals(e.get("event_type")))
                .toList();

        if (!completedEvents.isEmpty()) {
            // Artifact should exist for the node that completed
            int artifactCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM artifact WHERE workflow_node_instance_id = ?",
                    Integer.class, firstNodeId);
            assertThat(artifactCount).isGreaterThan(0);
        }
    }

    @Test
    void confirmationCreatedEvent_onlyAfterConfirmationInserted() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();

        // All CONFIRMATION_CREATED events should reference a confirmation that exists
        List<Map<String, Object>> confEvents = jdbcTemplate.queryForList(
                "SELECT payload_json FROM runtime_event WHERE event_type = 'CONFIRMATION_CREATED' " +
                        "AND workflow_instance_id = ?", instanceId);

        for (Map<String, Object> event : confEvents) {
            String payloadJson = (String) event.get("payload_json");
            if (payloadJson != null && payloadJson.contains("confirmationId")) {
                var payload = objectMapper.readTree(payloadJson);
                String confirmationId = payload.get("confirmationId").asText();

                int count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM confirmation_request WHERE id = ?",
                        Integer.class, confirmationId);
                assertThat(count).as("CONFIRMATION_CREATED event references non-existent confirmation: " + confirmationId)
                        .isGreaterThan(0);
            }
        }
    }
}
