package com.agentcenter.bridge.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSessionsReturns200() throws Exception {
        mockMvc.perform(get("/api/agent-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createSessionReturns201WithGeneratedId() throws Exception {
        mockMvc.perform(post("/api/agent-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sessionType": "GENERAL",
                                    "title": "Test Session",
                                    "runtimeType": "MOCK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sessionType", is("GENERAL")))
                .andExpect(jsonPath("$.title", is("Test Session")))
                .andExpect(jsonPath("$.runtimeType", is("MOCK")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void getSessionByIdReturnsCorrectData() throws Exception {
        var result = mockMvc.perform(post("/api/agent-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sessionType": "WORK_ITEM",
                                    "title": "Session for retrieval test",
                                    "runtimeType": "MOCK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/agent-sessions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.title", is("Session for retrieval test")));
    }

    @Test
    void getMessagesForSessionReturnsList() throws Exception {
        var result = mockMvc.perform(post("/api/agent-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sessionType": "GENERAL",
                                    "title": "Message test session",
                                    "runtimeType": "MOCK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/agent-sessions/" + id + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void sendMessageReturnsCreatedMessage() throws Exception {
        var result = mockMvc.perform(post("/api/agent-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sessionType": "GENERAL",
                                    "title": "Send message test session",
                                    "runtimeType": "MOCK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(post("/api/agent-sessions/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Hello, world!",
                                    "contentFormat": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sessionId", is(id)))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.content", is("Hello, world!")))
                .andExpect(jsonPath("$.contentFormat", is("TEXT")))
                .andExpect(jsonPath("$.seqNo", is(1)));

        mockMvc.perform(post("/api/agent-sessions/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Second message",
                                    "contentFormat": "MARKDOWN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seqNo", is(2)));
    }

    @Test
    void getNonexistentSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/agent-sessions/nonexistent-id"))
                .andExpect(status().isNotFound());
    }
}
