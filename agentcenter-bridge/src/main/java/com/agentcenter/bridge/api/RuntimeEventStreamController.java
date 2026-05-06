package com.agentcenter.bridge.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.infrastructure.event.SseEmitterRegistry;

@RestController
@RequestMapping("/api/agent-sessions")
public class RuntimeEventStreamController {

    private final RuntimeEventService eventService;
    private final SseEmitterRegistry emitterRegistry;

    public RuntimeEventStreamController(RuntimeEventService eventService,
                                         SseEmitterRegistry emitterRegistry) {
        this.eventService = eventService;
        this.emitterRegistry = emitterRegistry;
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String id) {
        SseEmitter emitter = emitterRegistry.createEmitter(id);

        eventService.getEventsBySession(id).forEach(event -> {
            try {
                emitter.send(SseEmitter.event()
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                emitterRegistry.createEmitter(id);
            }
        });

        return emitter;
    }
}
