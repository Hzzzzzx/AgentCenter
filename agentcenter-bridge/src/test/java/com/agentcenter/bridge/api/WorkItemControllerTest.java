package com.agentcenter.bridge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Test
    void listReturnsSeededWorkItems() throws Exception {
        mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'FE1234')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'US1203')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'BUG0602')]").exists());
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
