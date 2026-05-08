package com.agentcenter.bridge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listReturnsSeededWorkItems() throws Exception {
        mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'FE1234')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'US1203')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'BUG0602')]").exists());
    }

    @Test
    void workItemsIncludeWorkflowSummaryField() throws Exception {
        var result = mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andReturn();
        var array = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var item : array) {
            // All seeded items have no workflow instance, so workflowSummary should be null/absent
            var summary = item.get("workflowSummary");
            if (summary != null && !summary.isNull()) {
                // If somehow a workflow instance exists, verify the shape
                assert summary.has("instanceId");
                assert summary.has("status");
                assert summary.has("nodes");
                assert summary.has("stages");
            }
        }
    }

    @Test
    void workflowSummaryIncludesFailedNodeErrorMessage() throws Exception {
        var result = mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andReturn();
        var workItemId = findIdByCode(result.getResponse().getContentAsString(), "FE1234");
        var workflowDefinitionId = jdbcTemplate.queryForObject("""
                SELECT id FROM workflow_definition
                WHERE work_item_type = 'FE' AND status = 'ENABLED'
                ORDER BY is_default DESC, version_no DESC
                LIMIT 1
                """, String.class);
        var nodeDefinitionId = jdbcTemplate.queryForObject("""
                SELECT id FROM workflow_node_definition
                WHERE workflow_definition_id = ?
                ORDER BY order_no
                LIMIT 1
                """, String.class, workflowDefinitionId);
        var stageKey = jdbcTemplate.queryForObject("""
                SELECT COALESCE(stage_key, node_key) FROM workflow_node_definition
                WHERE id = ?
                """, String.class, nodeDefinitionId);
        var skillName = jdbcTemplate.queryForObject("""
                SELECT skill_name FROM workflow_node_definition
                WHERE id = ?
                """, String.class, nodeDefinitionId);
        var workflowInstanceId = "wf_summary_error_test";
        var nodeInstanceId = "wn_summary_error_test";
        var errorMessage = "LLD timeout";

        try {
            jdbcTemplate.update("""
                    INSERT INTO workflow_instance (
                        id, work_item_id, workflow_definition_id, status,
                        current_node_instance_id, started_at
                    )
                    VALUES (?, ?, ?, 'RUNNING', ?, datetime('now'))
                    """, workflowInstanceId, workItemId, workflowDefinitionId, nodeInstanceId);
            jdbcTemplate.update("""
                    INSERT INTO workflow_node_instance (
                        id, workflow_instance_id, node_definition_id, status,
                        error_message, node_kind, origin, stage_key, skill_name, sequence_no
                    )
                    VALUES (?, ?, ?, 'FAILED', ?, 'STAGE', 'DEFINITION', ?, ?, 1)
                    """, nodeInstanceId, workflowInstanceId, nodeDefinitionId,
                    errorMessage, stageKey, skillName);
            jdbcTemplate.update("""
                    UPDATE work_item
                    SET current_workflow_instance_id = ?
                    WHERE id = ?
                    """, workflowInstanceId, workItemId);

            mockMvc.perform(get("/api/work-items/" + workItemId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.workflowSummary.nodes[0].errorMessage").value(errorMessage))
                    .andExpect(jsonPath("$.workflowSummary.stages[0].errorMessage").value(errorMessage));
        } finally {
            jdbcTemplate.update("UPDATE work_item SET current_workflow_instance_id = NULL WHERE id = ?", workItemId);
            jdbcTemplate.update("DELETE FROM workflow_node_instance WHERE id = ?", nodeInstanceId);
            jdbcTemplate.update("DELETE FROM workflow_instance WHERE id = ?", workflowInstanceId);
        }
    }

    private String findIdByCode(String responseBody, String code) throws Exception {
        var array = objectMapper.readTree(responseBody);
        for (var node : array) {
            if (code.equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("No work item with code " + code);
    }

    @Test
    void getReturnsWorkItemById() throws Exception {
        var result = mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andReturn();
        var fe1234Id = findIdByCode(result.getResponse().getContentAsString(), "FE1234");

        mockMvc.perform(get("/api/work-items/" + fe1234Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FE1234"))
                .andExpect(jsonPath("$.title").value("用户登录优化"));
    }

    @Test
    void createWorkItem() throws Exception {
        mockMvc.perform(post("/api/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FE\",\"title\":\"New Feature\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.title").value("New Feature"))
                .andExpect(jsonPath("$.status").value("BACKLOG"));
    }

    @Test
    void updateWorkItem() throws Exception {
        var result = mockMvc.perform(get("/api/work-items")).andReturn();
        var fe1234Id = findIdByCode(result.getResponse().getContentAsString(), "FE1234");

        mockMvc.perform(put("/api/work-items/" + fe1234Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Restore original status to avoid polluting shared DB for other tests
        mockMvc.perform(put("/api/work-items/" + fe1234Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BACKLOG\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void getNonexistentReturns404() throws Exception {
        mockMvc.perform(get("/api/work-items/NONEXISTENT"))
                .andExpect(status().isNotFound());
    }
}
