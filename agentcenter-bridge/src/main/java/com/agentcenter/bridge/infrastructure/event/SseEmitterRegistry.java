package com.agentcenter.bridge.infrastructure.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;

@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long SSE_TIMEOUT_MS = 1_800_000L; // 30 minutes

    private final Map<String, List<SseEmitter>> sessionEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sessionEmitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(e -> removeEmitter(sessionId, emitter));
        return emitter;
    }

    public void sendToSession(String sessionId, Object data) {
        var emitters = sessionEmitters.getOrDefault(sessionId, List.of());
        for (var emitter : emitters) {
            try {
                emitter.send(sseEvent(data));
            } catch (Exception e) {
                removeEmitter(sessionId, emitter);
                if (isClientDisconnect(e)) {
                    log.debug("SSE client disconnected for session {}: {}", sessionId, e.getMessage());
                } else {
                    log.warn("SSE send failed for session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }

    public SseEmitter.SseEventBuilder sseEvent(Object data) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .data(data, MediaType.APPLICATION_JSON);
        if (data instanceof RuntimeEventDto event) {
            if (event.seqNo() != null) {
                builder.id(String.valueOf(event.seqNo()));
            } else if (event.id() != null && !event.id().isBlank()) {
                builder.id(event.id());
            }
        }
        return builder;
    }

    public void removeEmitter(String sessionId, SseEmitter emitter) {
        var list = sessionEmitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("clientabort")
                        || lower.contains("async request timed out")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
