package com.agentcenter.bridge.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
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
    public SseEmitter streamEvents(@PathVariable String id,
                                   @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                   @RequestParam(value = "afterSeq", required = false) Long afterSeq,
                                   @RequestParam(value = "limit", required = false) Integer limit) {
        SseEmitter emitter = emitterRegistry.createEmitter(id);
        Long cursor = afterSeq != null ? afterSeq : parseLastEventId(lastEventId);

        for (RuntimeEventDto event : eventService.getEventsBySession(id, cursor, limit)) {
            try {
                emitter.send(emitterRegistry.sseEvent(event));
            } catch (Exception e) {
                emitterRegistry.removeEmitter(id, emitter);
                emitter.completeWithError(e);
                break;
            }
        }

        return emitter;
    }

    private Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
