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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
class WorkflowMidSessionInputRoutingIntegrationTest {

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

    private StartedWorkflow startWorkflowAndWait(String workItemCode) throws Exception {
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

        java.util.List<java.util.Map<String, Object>> advanceConfirmations = jdbcTemplate.queryForList(
                "SELECT id FROM confirmation_request WHERE workflow_instance_id = ? AND workflow_node_instance_id = ? AND status = 'PENDING'",
                instanceId, firstNodeId);
        assertThat(advanceConfirmations).hasSize(1);
        String advanceConfirmationId = (String) advanceConfirmations.get(0).get("id");

        mockMvc.perform(post("/api/confirmations/" + advanceConfirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"ADVANCE\",\"comment\":\"进入下一节点\"}"))
                .andExpect(status().isOk());

        java.util.List<java.util.Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, workflow_node_instance_id, agent_session_id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        java.util.Map<String, Object> conf = confirmations.get(0);
        String confirmationId = (String) conf.get("id");
        String nodeInstanceId = (String) conf.get("workflow_node_instance_id");
        String sessionId = (String) conf.get("agent_session_id");

        return new StartedWorkflow(workItemId, instanceId, confirmationId, nodeInstanceId, sessionId);
    }

    @Test
    void supplementalInput_resumesCurrentNode() throws Exception {
        StartedWorkflow wf = startWorkflowAndWait("FE1234");

        int skillCountBefore = TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES.size();

        mockMvc.perform(post("/api/agent-sessions/" + wf.sessionId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"补充信息：需要支持移动端\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        var messages = jdbcTemplate.queryForList(
                "SELECT * FROM agent_message WHERE session_id = ? AND role = 'USER' AND created_by = 'system'",
                wf.sessionId());
        assertThat(messages).anySatisfy(m ->
                assertThat(m.get("content").toString()).contains("补充信息：需要支持移动端"));

        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES.size())
                .isGreaterThan(skillCountBefore);
        String lastInputContext = TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(
                TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.size() - 1);
        assertThat(lastInputContext).contains("方案设计 (HLD)");
    }

    @Test
    void advanceNext_completesNodeAndSchedulesNext() throws Exception {
        StartedWorkflow wf = startWorkflowAndWait("FE1234");

        mockMvc.perform(post("/api/agent-sessions/" + wf.sessionId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"跳过确认，直接推进\",\"workflowUserAction\":\"ADVANCE_NEXT\",\"workflowNodeInstanceId\":\""
                                + wf.nodeInstanceId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long completedCount = countNodesWithStatus(wfJson, "COMPLETED");
        assertThat(completedCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    void skipNode_skipsCurrentNode() throws Exception {
        StartedWorkflow wf = startWorkflowAndWait("FE1234");

        mockMvc.perform(post("/api/agent-sessions/" + wf.sessionId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"跳过此节点\",\"workflowUserAction\":\"SKIP_NODE\",\"workflowNodeInstanceId\":\""
                                + wf.nodeInstanceId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + wf.instanceId()))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        long skippedCount = countNodesWithStatus(wfJson, "SKIPPED");
        assertThat(skippedCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void pauseWorkflow_setsInstanceBlocked() throws Exception {
        StartedWorkflow wf = startWorkflowAndWait("FE1234");

        String instanceStatusBefore = jdbcTemplate.queryForObject(
                "SELECT status FROM workflow_instance WHERE id = ?", String.class, wf.instanceId());
        assertThat(instanceStatusBefore).isEqualTo("BLOCKED");

        jdbcTemplate.update(
                "UPDATE workflow_instance SET status = 'RUNNING' WHERE id = ?", wf.instanceId());

        mockMvc.perform(post("/api/agent-sessions/" + wf.sessionId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"暂停工作流\",\"workflowUserAction\":\"PAUSE_WORKFLOW\",\"workflowNodeInstanceId\":\""
                                + wf.nodeInstanceId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        String instanceStatusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM workflow_instance WHERE id = ?", String.class, wf.instanceId());
        assertThat(instanceStatusAfter).isEqualTo("BLOCKED");
    }

    @Test
    void continueCurrent_sendsRuntimeContinueWithoutReplayingNodePrompt() throws Exception {
        StartedWorkflow wf = startWorkflowAndWait("FE1234");
        jdbcTemplate.update("DELETE FROM confirmation_request WHERE id = ?", wf.confirmationId());
        jdbcTemplate.update("""
                UPDATE workflow_node_instance
                SET status = 'RUNNING', agent_state = 'IN_PROGRESS'
                WHERE id = ?
                """, wf.nodeInstanceId());
        jdbcTemplate.update("""
                UPDATE workflow_instance
                SET status = 'BLOCKED', current_node_instance_id = ?
                WHERE id = ?
                """, wf.nodeInstanceId(), wf.instanceId());

        int skillCountBefore = TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES.size();

        mockMvc.perform(post("/api/agent-sessions/" + wf.sessionId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"请继续当前节点，不要重新开始节点\","
                                + "\"workflowUserAction\":\"CONTINUE_CURRENT\","
                                + "\"workflowNodeInstanceId\":\"" + wf.nodeInstanceId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        String instanceStatusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM workflow_instance WHERE id = ?", String.class, wf.instanceId());
        assertThat(instanceStatusAfter).isEqualTo("RUNNING");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES).hasSize(skillCountBefore);
    }

    private long countNodesWithStatus(com.fasterxml.jackson.databind.JsonNode wfJson, String targetStatus) {
        var nodes = wfJson.at("/nodes");
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter(targetStatus::equals)
                .count();
    }

    record StartedWorkflow(String workItemId, String instanceId, String confirmationId,
                           String nodeInstanceId, String sessionId) {}
}
