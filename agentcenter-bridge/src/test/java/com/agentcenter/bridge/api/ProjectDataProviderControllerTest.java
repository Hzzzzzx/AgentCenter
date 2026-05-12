package com.agentcenter.bridge.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

            mockMvc.perform(post("/api/project-data-providers/sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providerId").value("fixture-beta"))
                    .andExpect(jsonPath("$.contexts[0].project").value("企业中台"));

            mockMvc.perform(get("/api/work-items")
                            .param("projectId", "企业中台")
                            .param("spaceId", "企业中台")
                            .param("iterationId", "Sprint 21"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code == 'B-FE201')]").exists())
                    .andExpect(jsonPath("$[?(@.code == 'B-US201')]").exists())
                    .andExpect(jsonPath("$[?(@.code == 'B-WORK201')]").doesNotExist());

            mockMvc.perform(get("/api/work-items/overview")
                            .param("projectId", "企业中台")
                            .param("spaceId", "企业中台")
                            .param("iterationId", "Sprint 21"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats[?(@.type == 'FE' && @.total == 1)]").exists())
                    .andExpect(jsonPath("$.stats[?(@.type == 'WORK' && @.total == 0)]").exists());
        } finally {
            mockMvc.perform(put("/api/project-data-providers/active")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"providerId\":\"fixture-alpha\"}"));
        }
    }
}
