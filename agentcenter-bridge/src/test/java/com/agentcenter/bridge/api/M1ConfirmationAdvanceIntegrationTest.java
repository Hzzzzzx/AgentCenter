package com.agentcenter.bridge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        // Step 1: Start workflow for FE1234
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInstance.status").value("RUNNING"))
                .andExpect(jsonPath("$.confirmation").exists())
                .andExpect(jsonPath("$.confirmation.status").value("PENDING"))
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String confirmationId = startJson.at("/confirmation/id").asText();
        String instanceId = startJson.at("/workflowInstance/id").asText();
        assertThat(confirmationId).isNotEmpty();

        // Step 2: List pending confirmations - should include our new one
        mockMvc.perform(get("/api/confirmations?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + confirmationId + "')]").exists());

        // Step 3: Get confirmation details
        mockMvc.perform(get("/api/confirmations/" + confirmationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.title").exists());

        // Step 4: Enter session
        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/enter-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_CONVERSATION"));

        // Step 5: Resolve with APPROVE - this should advance the workflow
        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPROVE\",\"comment\":\"Confirm and continue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Step 6: Verify workflow advanced - get the instance
        mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.nodes[?(@.status == 'COMPLETED')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.status == 'WAITING_CONFIRMATION')]").exists());
    }

    @Test
    void rejectConfirmation_doesNotAdvanceWorkflow() throws Exception {
        // Start workflow
        String fe1234Id = findWorkItemIdByCode("FE1234");

        MvcResult startResult = mockMvc.perform(
                        post("/api/work-items/" + fe1234Id + "/start-workflow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        var startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String confirmationId = startJson.at("/confirmation/id").asText();
        String instanceId = startJson.at("/workflowInstance/id").asText();

        // Reject
        mockMvc.perform(post("/api/confirmations/" + confirmationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify no node is COMPLETED - the first node stays WAITING_CONFIRMATION
        mockMvc.perform(get("/api/workflow-instances/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.status == 'COMPLETED')]").doesNotExist());
    }
}
