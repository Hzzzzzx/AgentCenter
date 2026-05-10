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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
class M1ConfirmationAdvanceIntegrationTest {

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

    private MvcResult startAndAdvanceToSecondNode(String workItemId) throws Exception {
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

        return mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void fullM1Flow_startWorkflow_resolveConfirmation_resumesCurrentSkill() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult advancedResult = startAndAdvanceToSecondNode(fe1234Id);
        var advancedJson = objectMapper.readTree(advancedResult.getResponse().getContentAsString());
        String instanceId = advancedJson.at("/id").asText();

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, request_type, status, options_json FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        String confirmationId = (String) confirmations.get(0).get("id");
        String optionsJson = (String) confirmations.get(0).get("options_json");
        assertThat(optionsJson).contains("低风险方案");

        int artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artifact WHERE workflow_instance_id = ?", Integer.class, instanceId);
        assertThat(artifactCount).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/confirmations/" + confirmationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestType").value("DECISION"))
                .andExpect(jsonPath("$.workflowInstanceId").value(instanceId));

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"comment\":\"低风险方案\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        assertThat(wfJson.get("status").asText()).isEqualTo("BLOCKED");

        var nodes = wfJson.at("/nodes");
        var completedStatuses = java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter("COMPLETED"::equals)
                .count();
        assertThat(completedStatuses).isEqualTo(1);
        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES).contains("hld-design");
        int hldInputIndex = TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES.lastIndexOf("hld-design");
        String hldInputContext = TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(hldInputIndex);
        assertThat(hldInputContext)
                .contains("## 工作项")
                .contains("FE1234")
                .contains("用户登录优化")
                .contains("## 上游产物")
                .contains("artifactId")
                .contains("测试 PRD 输出")
                .contains("## 当前节点")
                .contains("hld-design")
                .contains("用户交互回答历史")
                .contains("低风险方案");
    }

    @Test
    void rejectConfirmation_doesNotAdvanceWorkflow() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult advancedResult = startAndAdvanceToSecondNode(fe1234Id);
        var advancedJson = objectMapper.readTree(advancedResult.getResponse().getContentAsString());
        String instanceId = advancedJson.at("/id").asText();

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        String confirmationId = (String) confirmations.get(0).get("id");

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        var nodes = wfJson.at("/nodes");
        long completedCount = java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter("COMPLETED"::equals)
                .count();
        assertThat(completedCount).isEqualTo(1);
    }

    @Test
    void resolvingOneConfirmation_keepsNodeBlockedWhenAnotherInteractionIsInConversation() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult advancedResult = startAndAdvanceToSecondNode(fe1234Id);
        var advancedJson = objectMapper.readTree(advancedResult.getResponse().getContentAsString());
        String instanceId = advancedJson.at("/id").asText();

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, workflow_node_instance_id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        String confirmationId = (String) confirmations.get(0).get("id");
        String nodeInstanceId = (String) confirmations.get(0).get("workflow_node_instance_id");

        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, title, priority, created_at, updated_at
                )
                VALUES (?, 'INPUT_REQUIRED', 'IN_CONVERSATION', ?, ?, ?, '补充信息', 'MEDIUM', datetime('now'), datetime('now'))
                """, "conf-blocker", fe1234Id, instanceId, nodeInstanceId);

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"comment\":\"低风险方案\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        var nodes = wfJson.at("/nodes");
        assertThat(java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .anyMatch(n -> nodeInstanceId.equals(n.get("id").asText())
                        && "WAITING_CONFIRMATION".equals(n.get("status").asText()))).isTrue();
    }

    @Test
    void resolvingLastBlockingConfirmation_resumesCurrentNode() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult advancedResult = startAndAdvanceToSecondNode(fe1234Id);
        var advancedJson = objectMapper.readTree(advancedResult.getResponse().getContentAsString());
        String instanceId = advancedJson.at("/id").asText();

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT id, workflow_node_instance_id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        String confirmationId = (String) confirmations.get(0).get("id");
        String nodeInstanceId = (String) confirmations.get(0).get("workflow_node_instance_id");

        jdbcTemplate.update("""
                INSERT INTO confirmation_request (
                    id, request_type, status, work_item_id, workflow_instance_id,
                    workflow_node_instance_id, title, priority, created_at, updated_at
                )
                VALUES (?, 'INPUT_REQUIRED', 'IN_CONVERSATION', ?, ?, ?, '补充信息', 'MEDIUM', datetime('now'), datetime('now'))
                """, "conf-last-blocker", fe1234Id, instanceId, nodeInstanceId);

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CHOOSE\",\"payload\":{\"choice\":\"低风险方案\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/confirmations/conf-last-blocker/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SUPPLEMENT\",\"payload\":{\"input\":\"补充完成\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        assertThat(wfJson.at("/currentNodeInstanceId").asText()).isEqualTo(nodeInstanceId);

        var nodes = wfJson.at("/nodes");
        assertThat(java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .anyMatch(n -> nodeInstanceId.equals(n.get("id").asText())
                        && "WAITING_CONFIRMATION".equals(n.get("status").asText()))).isTrue();
        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES).contains("hld-design");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(
                TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.size() - 1))
                .contains("用户交互回答历史")
                .contains("补充完成");
    }

    @Test
    void continueWorkflow_conflictsWhenCurrentNodeIsRunning() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult advancedResult = startAndAdvanceToSecondNode(fe1234Id);
        var advancedJson = objectMapper.readTree(advancedResult.getResponse().getContentAsString());
        String instanceId = advancedJson.at("/id").asText();

        List<Map<String, Object>> confirmations = jdbcTemplate.queryForList(
                "SELECT workflow_node_instance_id FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(confirmations).hasSize(1);
        String nodeInstanceId = (String) confirmations.get(0).get("workflow_node_instance_id");

        jdbcTemplate.update("UPDATE workflow_node_instance SET status = 'RUNNING' WHERE id = ?", nodeInstanceId);
        jdbcTemplate.update("""
                UPDATE workflow_instance
                SET status = 'RUNNING', current_node_instance_id = ?
                WHERE id = ?
                """, nodeInstanceId, instanceId);

        mockMvc.perform(post("/api/workflow-instances/" + instanceId + "/continue"))
                .andExpect(status().isConflict());
    }
}
