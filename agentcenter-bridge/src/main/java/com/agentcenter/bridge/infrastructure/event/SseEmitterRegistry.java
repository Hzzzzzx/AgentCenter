package com.agentcenter.bridge.infrastructure.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes

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
                emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                removeEmitter(sessionId, emitter);
            }
        }
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        var list = sessionEmitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
