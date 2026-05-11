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
        assertThat(firstNode.get("status").asText()).isEqualTo("WAITING_CONFIRMATION");
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
        assertThat(secondNode.get("status").asText()).isEqualTo("PENDING");

        var thirdNode = json.at("/workflowInstance/nodes/2");
        assertThat(thirdNode.get("status").asText()).isEqualTo("PENDING");

        var artifacts = json.get("artifacts");
        assertThat(artifacts.size()).isGreaterThanOrEqualTo(1);

        var events = json.get("events");
        assertThat(events.size()).isGreaterThan(0);
        var eventTypes = java.util.stream.StreamSupport.stream(events.spliterator(), false)
                .map(e -> e.get("eventType").asText())
                .toList();
        assertThat(eventTypes).contains("SKILL_STARTED", "SKILL_COMPLETED");
        for (var event : events) {
            if (event.get("eventType").asText().startsWith("SKILL_")) {
                assertThat(objectMapper.readTree(event.get("payloadJson").asText()).hasNonNull("toolCallId")).isTrue();
            }
        }
        assertThat(java.util.stream.StreamSupport.stream(events.spliterator(), false)
                .allMatch(e -> instanceId.equals(e.get("workflowInstanceId").asText()))).isTrue();

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
    void startWorkflow_autoMode_advancesAfterReadyNodeUntilUserInput() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"AUTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance.status").value("BLOCKED"))
                .andExpect(jsonPath("$.confirmation").exists())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String instanceId = json.at("/workflowInstance/id").asText();

        assertThat(json.at("/workflowInstance/nodes/0/status").asText()).isEqualTo("COMPLETED");
        assertThat(json.at("/workflowInstance/nodes/1/status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(json.at("/workflowInstance/nodes/2/status").asText()).isEqualTo("PENDING");
        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES)
                .contains("prd-desingn", "hld-design");

        String executionMode = jdbcTemplate.queryForObject(
                "SELECT execution_mode FROM workflow_instance WHERE id = ?",
                String.class, instanceId);
        assertThat(executionMode).isEqualTo("AUTO");

        String waitingNodeId = json.at("/workflowInstance/nodes/1/id").asText();
        var pendingConfirmations = jdbcTemplate.queryForList(
                "SELECT request_type, workflow_node_instance_id, interaction_type FROM confirmation_request WHERE workflow_instance_id = ? AND status = 'PENDING'",
                instanceId);
        assertThat(pendingConfirmations).hasSize(1);
        assertThat(pendingConfirmations.get(0).get("request_type")).isEqualTo("DECISION");
        assertThat(pendingConfirmations.get(0).get("workflow_node_instance_id")).isEqualTo(waitingNodeId);
        assertThat(pendingConfirmations.get(0).get("interaction_type")).isNotEqualTo("WORKFLOW_ADVANCE");
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
    void startWorkflow_existingPendingInstance_returnsPreparedSessionAndInputMessage() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");
        String definitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_definition WHERE work_item_type = 'FE' AND status = 'ENABLED' AND is_default = 1 LIMIT 1",
                String.class);
        var nodeDefs = jdbcTemplate.queryForList(
                "SELECT id, name, order_no, skill_name, stage_key FROM workflow_node_definition WHERE workflow_definition_id = ? ORDER BY order_no",
                definitionId);
        String instanceId = "01TESTEXISTINGPENDINGWF000001";
        String firstNodeId = "01TESTEXISTINGPENDINGNODE001";

        jdbcTemplate.update("""
                INSERT INTO workflow_instance (
                    id, work_item_id, workflow_definition_id, status, execution_mode,
                    current_node_instance_id, started_at
                ) VALUES (?, ?, ?, 'RUNNING', 'MANUAL_CONFIRM', ?, datetime('now'))
                """, instanceId, fe1234Id, definitionId, firstNodeId);

        for (int i = 0; i < nodeDefs.size(); i++) {
            var nodeDef = nodeDefs.get(i);
            String nodeId = i == 0 ? firstNodeId : "01TESTEXISTINGPENDINGNODE00" + (i + 1);
            jdbcTemplate.update("""
                    INSERT INTO workflow_node_instance (
                        id, workflow_instance_id, node_definition_id, status, version,
                        node_kind, origin, stage_key, skill_name, summary, sequence_no
                    ) VALUES (?, ?, ?, 'PENDING', 1, 'STAGE', 'DEFINITION', ?, ?, ?, ?)
                    """,
                    nodeId,
                    instanceId,
                    nodeDef.get("id"),
                    nodeDef.get("stage_key"),
                    nodeDef.get("skill_name"),
                    nodeDef.get("name"),
                    nodeDef.get("order_no"));
        }

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session").exists())
                .andExpect(jsonPath("$.workflowInstance.id").value(instanceId))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = json.at("/session/id").asText();
        assertThat(sessionId).isNotBlank();

        var workflowInputs = jdbcTemplate.queryForList(
                "SELECT role, content, workflow_node_instance_id FROM agent_message WHERE session_id = ? AND role = 'USER'",
                sessionId);
        assertThat(workflowInputs).hasSize(1);
        assertThat(workflowInputs.get(0).get("workflow_node_instance_id")).isEqualTo(firstNodeId);
        assertThat(workflowInputs.get(0).get("content").toString())
                .contains("请执行工作流节点")
                .contains("工作项编号：FE1234");

        String currentWorkflowInstanceId = jdbcTemplate.queryForObject(
                "SELECT current_workflow_instance_id FROM work_item WHERE id = ?",
                String.class, fe1234Id);
        assertThat(currentWorkflowInstanceId).isEqualTo(instanceId);
    }

    @Test
    void startWorkflow_runtimeSessionUnavailable_recordsClearFailureInsteadOfNullPointer() throws Exception {
        TestWorkflowExecutorConfig.setEnsureSessionError(new IllegalStateException("opencode serve is unavailable"));
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult result = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\": \"START_OR_CONTINUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance.status").value("BLOCKED"))
                .andExpect(jsonPath("$.workflowInstance.nodes[0].status").value("FAILED"))
                .andExpect(jsonPath("$.workflowInstance.nodes[0].errorMessage")
                        .value("Runtime session is not ready. Please check opencode serve and retry this node."))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = json.at("/session/id").asText();
        assertThat(sessionId).isNotBlank();
        String nodeId = json.at("/workflowInstance/nodes/0/id").asText();

        var confirmations = jdbcTemplate.queryForList(
                "SELECT title, content FROM confirmation_request WHERE workflow_node_instance_id = ? AND status = 'PENDING'",
                nodeId);
        assertThat(confirmations).hasSize(1);
        assertThat(confirmations.get(0).get("content").toString())
                .contains("Runtime session is not ready")
                .doesNotContain("NullPointerException");

        var messages = jdbcTemplate.queryForList(
                "SELECT role, content FROM agent_message WHERE session_id = ? ORDER BY seq_no",
                sessionId);
        assertThat(messages).anySatisfy(message -> {
            assertThat(message.get("role")).isEqualTo("USER");
            assertThat(message.get("content").toString()).contains("请执行工作流节点");
        });

        TestWorkflowExecutorConfig.setEnsureSessionError(null);
        TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES.clear();
        mockMvc.perform(post("/api/workflow-node-instances/" + nodeId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance.nodes[0].status").value("WAITING_CONFIRMATION"));

        assertThat(TestWorkflowExecutorConfig.CAPTURED_SKILL_NAMES).contains("prd-desingn");
        String runtimeSessionId = jdbcTemplate.queryForObject(
                "SELECT runtime_session_id FROM workflow_node_instance WHERE id = ?",
                String.class, nodeId);
        assertThat(runtimeSessionId).startsWith("stub-session-");
        Integer activeExceptionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM confirmation_request
                WHERE workflow_node_instance_id = ?
                  AND request_type = 'EXCEPTION'
                  AND status IN ('PENDING', 'IN_CONVERSATION')
                """, Integer.class, nodeId);
        assertThat(activeExceptionCount).isZero();
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

        String firstNodeId = json.at("/workflowInstance/nodes/0/id").asText();
        String sessionId = json.at("/workflowInstance/nodes/0/agentSessionId").asText();

        mockMvc.perform(post("/api/agent-sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"advance\",\"workflowUserAction\":\"ADVANCE_NEXT\",\"workflowNodeInstanceId\":\"" + firstNodeId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workflow-instances/" + instanceId + "/continue"))
                .andExpect(status().isConflict());
    }

    @Test
    void skipNode_onReadyNode_skipsAndAdvances() throws Exception {
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
        assertThat(skipJson.at("/workflowInstance/status").asText()).isIn("RUNNING", "BLOCKED");
    }

    @Test
    void listWorkflowDefinitions_returnsSeedData() throws Exception {
        mockMvc.perform(get("/api/workflow-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.workItemType == 'FE')]").exists());
    }
}
