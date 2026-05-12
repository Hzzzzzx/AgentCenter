package com.agentcenter.bridge.api;

import com.agentcenter.bridge.application.TestWorkflowExecutorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWorkflowExecutorConfig.class)
class BatchWorkflowStartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
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

    @Test
    void batchStartWorkflows_requiresMatchingTypeAndHonorsLimit() throws Exception {
        List<String> feIds = jdbcTemplate.queryForList(
                "SELECT id FROM work_item WHERE type = 'FE' ORDER BY code LIMIT 2",
                String.class);
        assertThat(feIds).hasSize(2);

        String body = """
                {
                  "workItemType": "FE",
                  "workItemIds": ["%s", "%s"],
                  "limit": 1,
                  "mode": "START_OR_CONTINUE"
                }
                """.formatted(feIds.get(0), feIds.get(1));

        MvcResult result = mockMvc.perform(post("/api/work-items/start-workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemType").value("FE"))
                .andExpect(jsonPath("$.requestedCount").value(2))
                .andExpect(jsonPath("$.effectiveLimit").value(1))
                .andExpect(jsonPath("$.startedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.results[0].status").value("STARTED"))
                .andExpect(jsonPath("$.results[1].status").value("SKIPPED"))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.at("/results/1/reason").asText()).contains("上限");

        Integer startedInstances = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instance WHERE work_item_id IN (?, ?)",
                Integer.class,
                feIds.get(0),
                feIds.get(1));
        assertThat(startedInstances).isEqualTo(1);
    }

    @Test
    void batchStartWorkflows_skipsItemsOutsideSelectedType() throws Exception {
        String feId = jdbcTemplate.queryForObject(
                "SELECT id FROM work_item WHERE type = 'FE' ORDER BY code LIMIT 1",
                String.class);
        String usId = jdbcTemplate.queryForObject(
                "SELECT id FROM work_item WHERE type = 'US' ORDER BY code LIMIT 1",
                String.class);

        String body = """
                {
                  "workItemType": "FE",
                  "workItemIds": ["%s", "%s"],
                  "limit": 5,
                  "mode": "START_OR_CONTINUE"
                }
                """.formatted(feId, usId);

        mockMvc.perform(post("/api/work-items/start-workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.results[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.results[1].reason").value("工作项类型与当前筛选类型不一致"));

        Integer usInstances = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instance WHERE work_item_id = ?",
                Integer.class,
                usId);
        assertThat(usInstances).isZero();
    }
}
