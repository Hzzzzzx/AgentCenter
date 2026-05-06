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
class M1ConfirmationAdvanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanWorkflowData() {
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
    void fullM1Flow_startWorkflow_resolveConfirmation_advancesToNextNode() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance.status").value("RUNNING"))
                .andExpect(jsonPath("$.confirmation").exists())
                .andExpect(jsonPath("$.confirmation.id").isNotEmpty())
                .andExpect(jsonPath("$.artifacts").isArray())
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String instanceId = startJson.at("/workflowInstance/id").asText();
        String confirmationId = startJson.at("/confirmation/id").asText();
        assertThat(confirmationId).isNotEmpty();

        assertThat(startJson.get("artifacts").size()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/confirmations/" + confirmationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.workflowInstanceId").value(instanceId));

        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPROVE\",\"comment\":\"Confirm and continue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        MvcResult wfResult = mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andReturn();
        var wfJson = objectMapper.readTree(wfResult.getResponse().getContentAsString());
        assertThat(wfJson.get("status").asText()).isIn("RUNNING", "COMPLETED");

        var nodes = wfJson.at("/nodes");
        var completedStatuses = java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .map(n -> n.get("status").asText())
                .filter("COMPLETED"::equals)
                .count();
        assertThat(completedStatuses).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rejectConfirmation_doesNotAdvanceWorkflow() throws Exception {
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation").exists())
                .andExpect(jsonPath("$.confirmation.id").isNotEmpty())
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String confirmationId = startJson.at("/confirmation/id").asText();
        String instanceId = startJson.at("/workflowInstance/id").asText();
        assertThat(confirmationId).isNotEmpty();

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
}
