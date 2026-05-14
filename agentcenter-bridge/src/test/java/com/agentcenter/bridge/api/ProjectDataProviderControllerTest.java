package com.agentcenter.bridge.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectDataProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void switchesProviderAndSyncsScopedFixtureWorkItems() throws Exception {
        try {
            mockMvc.perform(get("/api/project-data-providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providers[?(@.id == 'fixture-alpha')]").exists())
                    .andExpect(jsonPath("$.providers[?(@.id == 'fixture-beta')]").exists());

            mockMvc.perform(put("/api/project-data-providers/active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"providerId\":\"fixture-beta\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProviderId").value("fixture-beta"));

            mockMvc.perform(put("/api/project-data-providers/active-scope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "providerId":"fixture-beta",
                                      "projectName":"企业车",
                                      "externalProjectId":"beta-project-enterprise",
                                      "externalSpaceId":"beta-space-enterprise",
                                      "externalIterationId":"beta-sprint-21"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProjectName").value("企业车"))
                    .andExpect(jsonPath("$.activeExternalProjectId").value("beta-project-enterprise"))
                    .andExpect(jsonPath("$.activeExternalSpaceId").value("beta-space-enterprise"))
                    .andExpect(jsonPath("$.activeExternalIterationId").value("beta-sprint-21"));

            mockMvc.perform(post("/api/project-data-providers/sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providerId").value("fixture-beta"))
                    .andExpect(jsonPath("$.contexts[0].project").value("企业中台"))
                    .andExpect(jsonPath("$.syncStats.total").value(6));

            mockMvc.perform(get("/api/project-data-providers/sync-history")
                            .param("providerId", "fixture-beta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].providerId").value("fixture-beta"))
                    .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$[0].workItemCount").value(6));

            mockMvc.perform(get("/api/project-data-providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProviderId").value("fixture-beta"))
                    .andExpect(jsonPath("$.activeProjectContextId").isNotEmpty())
                    .andExpect(jsonPath("$.activeProjectSpaceId").isNotEmpty())
                    .andExpect(jsonPath("$.activeProjectIterationId").isNotEmpty());

            mockMvc.perform(get("/api/work-items")
                            .param("providerId", "fixture-beta")
                            .param("projectId", "fixture-beta:beta-project-enterprise")
                            .param("spaceId", "beta-space-enterprise")
                            .param("iterationId", "beta-sprint-21"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code == 'B-FE201')]").exists())
                    .andExpect(jsonPath("$[?(@.code == 'B-US201')]").exists())
                    .andExpect(jsonPath("$[?(@.code == 'B-WORK201')]").doesNotExist());

            mockMvc.perform(get("/api/work-items/overview")
                            .param("providerId", "fixture-beta")
                            .param("projectId", "fixture-beta:beta-project-enterprise")
                            .param("spaceId", "beta-space-enterprise")
                            .param("iterationId", "beta-sprint-21"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats[?(@.type == 'FE' && @.total == 1)]").exists())
                    .andExpect(jsonPath("$.stats[?(@.type == 'WORK' && @.total == 0)]").exists());

            mockMvc.perform(get("/api/workflow-definitions")
                            .param("projectId", "fixture-beta:beta-project-enterprise"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.projectId == 'fixture-beta:beta-project-enterprise' && @.workItemType == 'FE' && @.isDefault == true)]").exists());

            mockMvc.perform(get("/api/workflow-definitions")
                            .param("projectId", "fixture-beta:beta-project-security"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.projectId == 'fixture-beta:beta-project-security' && @.workItemType == 'FE' && @.isDefault == true)]").exists());

            mockMvc.perform(put("/api/project-data-providers/active-scope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "providerId":"fixture-beta",
                                      "projectName":"安全治理",
                                      "externalProjectId":"beta-project-security",
                                      "externalSpaceId":"beta-space-security",
                                      "externalIterationId":"beta-long-governance"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProviderId").value("fixture-beta"))
                    .andExpect(jsonPath("$.activeProjectName").value("安全治理"))
                    .andExpect(jsonPath("$.activeExternalProjectId").value("beta-project-security"))
                    .andExpect(jsonPath("$.activeExternalSpaceId").value("beta-space-security"))
                    .andExpect(jsonPath("$.activeExternalIterationId").value("beta-long-governance"));

            Integer provisionedFeWorkflowCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM workflow_definition
                    WHERE project_id IN ('fixture-beta:beta-project-enterprise', 'fixture-beta:beta-project-security')
                      AND work_item_type = 'FE'
                      AND status = 'ENABLED'
                      AND is_default = 1
                    """, Integer.class);
            assertEquals(2, provisionedFeWorkflowCount);

            Integer provisionedFeNodeCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM workflow_node_definition wnd
                    JOIN workflow_definition wd ON wd.id = wnd.workflow_definition_id
                    WHERE wd.project_id = 'fixture-beta:beta-project-enterprise'
                      AND wd.work_item_type = 'FE'
                    """, Integer.class);
            assertTrue(provisionedFeNodeCount != null && provisionedFeNodeCount > 0);

            mockMvc.perform(post("/api/project-data-providers/sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providerId").value("fixture-beta"))
                    .andExpect(jsonPath("$.syncStats.total").value(6));

            mockMvc.perform(get("/api/project-data-providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProviderId").value("fixture-beta"))
                    .andExpect(jsonPath("$.activeExternalProjectId").value("beta-project-security"))
                    .andExpect(jsonPath("$.activeExternalSpaceId").value("beta-space-security"))
                    .andExpect(jsonPath("$.activeExternalIterationId").value("beta-long-governance"));

            Integer provisionedFeWorkflowCountAfterResync = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM workflow_definition
                    WHERE project_id IN ('fixture-beta:beta-project-enterprise', 'fixture-beta:beta-project-security')
                      AND work_item_type = 'FE'
                      AND status = 'ENABLED'
                      AND is_default = 1
                    """, Integer.class);
            assertEquals(2, provisionedFeWorkflowCountAfterResync);

            Integer contextCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM project_context
                    WHERE provider_id = 'fixture-beta'
                    """, Integer.class);
            assertTrue(contextCount != null && contextCount >= 2);

            String sprint22IterationId = jdbcTemplate.queryForObject("""
                    SELECT id FROM project_iteration
                    WHERE provider_id = 'fixture-beta' AND iteration_name = 'Sprint 22'
                    """, String.class);
            String workItemIterationId = jdbcTemplate.queryForObject("""
                    SELECT project_iteration_id FROM work_item
                    WHERE code = 'B-WORK201'
                    """, String.class);
            assertEquals(sprint22IterationId, workItemIterationId);
        } finally {
            mockMvc.perform(put("/api/project-data-providers/active")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"providerId\":\"fixture-alpha\"}"));
        }
    }
}
