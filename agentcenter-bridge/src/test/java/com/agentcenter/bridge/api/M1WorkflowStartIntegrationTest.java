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
class M1WorkflowStartIntegrationTest {

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

    @Test
    void startWorkflow_createsInstanceAndRunsFirstNode() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        String requestBody = """
                {"mode": "START_OR_CONTINUE"}
                """;

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance").exists())
                .andExpect(jsonPath("$.workflowInstance.status").value("BLOCKED"))
                .andExpect(jsonPath("$.workflowInstance.nodes").isArray())
                .andExpect(jsonPath("$.workflowInstance.nodes.length()").value(3))
                .andExpect(jsonPath("$.artifacts").isArray())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.confirmation").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var json = objectMapper.readTree(responseBody);

        String instanceId = json.at("/workflowInstance/id").asText();
        assertThat(instanceId).isNotBlank();

        var firstNode = json.at("/workflowInstance/nodes/0");
        assertThat(firstNode.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(firstNode.get("outputArtifactId").asText()).isNotEmpty();
        assertThat(TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS).isNotEmpty();
        String firstInputContext = TestWorkflowExecutorConfig.CAPTURED_INPUT_CONTEXTS.get(0);
        assertThat(firstInputContext)
                .contains("## 工作项")
                .contains("编号")
                .contains("FE1234")
                .contains("用户登录优化")
                .contains("优先级")
                .contains("## 当前节点")
                .contains("Skill")
                .contains("prd-desingn");

        String firstNodeSessionId = firstNode.get("agentSessionId").asText();
        var userInputs = jdbcTemplate.queryForList(
                "SELECT role, content FROM agent_message WHERE session_id = ? AND role = 'USER' ORDER BY seq_no",
                firstNodeSessionId);
        assertThat(userInputs).hasSizeGreaterThanOrEqualTo(1);
        String workflowUserInput = userInputs.get(0).get("content").toString();
        assertThat(workflowUserInput)
                .contains("请执行工作流节点")
                .contains("工作项编号：FE1234")
                .contains("工作项标题：用户登录优化")
                .contains("使用 Skill：prd-desingn")
                .contains("## 任务信息")
                .contains("## 节点上下文");

        var secondNode = json.at("/workflowInstance/nodes/1");
        assertThat(secondNode.get("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(secondNode.get("agentSessionId").asText()).isNotEmpty();

        var thirdNode = json.at("/workflowInstance/nodes/2");
        assertThat(thirdNode.get("status").asText()).isEqualTo("PENDING");

        var artifacts = json.get("artifacts");
        assertThat(artifacts.size()).isGreaterThanOrEqualTo(1);

        var events = json.get("events");
        assertThat(events.size()).isGreaterThan(0);
        var eventTypes = java.util.stream.StreamSupport.stream(events.spliterator(), false)
                .map(e -> e.get("eventType").asText())
                .toList();
        assertThat(eventTypes).contains("SKILL_STARTED", "SKILL_COMPLETED", "CONFIRMATION_CREATED");
        assertThat(java.util.stream.StreamSupport.stream(events.spliterator(), false)
                .allMatch(e -> instanceId.equals(e.get("workflowInstanceId").asText()))).isTrue();

        var confirmation = json.get("confirmation");
        assertThat(confirmation).isNotNull();
        assertThat(confirmation.get("id").asText()).isNotEmpty();
        assertThat(confirmation.get("requestType").asText()).isEqualTo("DECISION");
        assertThat(confirmation.get("optionsJson").asText()).contains("低风险方案", "低成本方案", "完整方案");

        MvcResult workItemResult = mockMvc.perform(get("/api/work-items/" + fe1234Id))
                .andExpect(status().isOk())
                .andReturn();
        var workItemJson = objectMapper.readTree(workItemResult.getResponse().getContentAsString());
        assertThat(workItemJson.get("currentWorkflowInstanceId").asText()).isEqualTo(instanceId);

        mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instanceId))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void startWorkflow_returns404_forNonexistentWorkItem() throws Exception {
        mockMvc.perform(post("/api/work-items/NONEXISTENT/start-workflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void startWorkflow_idempotent_returnsSameInstance() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult first = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String firstInstanceId = firstJson.at("/workflowInstance/id").asText();

        MvcResult second = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        String secondInstanceId = secondJson.at("/workflowInstance/id").asText();

        assertThat(secondInstanceId).isEqualTo(firstInstanceId);
    }

    @Test
    void continueWorkflow_conflictWhenWaitingConfirmation() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();

        mockMvc.perform(post("/api/workflow-instances/" + instanceId + "/continue"))
                .andExpect(status().isConflict());
    }

    @Test
    void skipNode_onCompletedNode_skipsWaitingConfirmationAndAdvances() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String currentNodeId = json.at("/workflowInstance/currentNodeInstanceId").asText();

        MvcResult skipResult = mockMvc.perform(
                        post("/api/workflow-node-instances/" + currentNodeId + "/skip"))
                .andExpect(status().isOk())
                .andReturn();

        var skipJson = objectMapper.readTree(skipResult.getResponse().getContentAsString());
        assertThat(skipJson.at("/workflowInstance/status").asText()).isIn("RUNNING", "COMPLETED");
    }

    @Test
    void listWorkflowDefinitions_returnsSeedData() throws Exception {
        mockMvc.perform(get("/api/workflow-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.workItemType == 'FE')]").exists());
    }
}
