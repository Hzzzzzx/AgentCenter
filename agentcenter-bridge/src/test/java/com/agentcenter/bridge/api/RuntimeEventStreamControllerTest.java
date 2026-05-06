package com.agentcenter.bridge.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeEventStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getEventsStartsAsyncProcessing() throws Exception {
        mockMvc.perform(get("/api/agent-sessions/some-session-id/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void getEventsForExistingSessionStartsAsyncProcessing() throws Exception {
        var createResult = mockMvc.perform(post("/api/agent-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sessionType": "GENERAL",
                                    "title": "SSE test session",
                                    "runtimeType": "MOCK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/agent-sessions/" + id + "/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
