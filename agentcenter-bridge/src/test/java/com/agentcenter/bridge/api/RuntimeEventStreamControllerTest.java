package com.agentcenter.bridge.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.infrastructure.event.SseEmitterRegistry;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Test
    void streamEventsUsesAfterSeqOverLastEventId() {
        RuntimeEventService eventService = mock(RuntimeEventService.class);
        SseEmitterRegistry emitterRegistry = mock(SseEmitterRegistry.class);
        when(emitterRegistry.createEmitter("ses-1")).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(1000L));
        when(eventService.getEventsBySession("ses-1", 8L, 12)).thenReturn(List.of());

        RuntimeEventStreamController controller = new RuntimeEventStreamController(eventService, emitterRegistry);

        controller.streamEvents("ses-1", "7", 8L, 12);

        verify(eventService).getEventsBySession("ses-1", 8L, 12);
    }

    @Test
    void streamEventsFallsBackToLastEventIdCursor() {
        RuntimeEventService eventService = mock(RuntimeEventService.class);
        SseEmitterRegistry emitterRegistry = mock(SseEmitterRegistry.class);
        when(emitterRegistry.createEmitter("ses-1")).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(1000L));
        when(eventService.getEventsBySession("ses-1", 7L, 300)).thenReturn(List.of());

        RuntimeEventStreamController controller = new RuntimeEventStreamController(eventService, emitterRegistry);

        controller.streamEvents("ses-1", "7", null, 300);

        verify(eventService).getEventsBySession("ses-1", 7L, 300);
    }
}
