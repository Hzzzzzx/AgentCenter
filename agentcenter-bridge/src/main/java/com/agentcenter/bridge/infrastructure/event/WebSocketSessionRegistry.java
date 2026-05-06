package com.agentcenter.bridge.infrastructure.event;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, List<WebSocketSession>> sessionsByAgentSessionId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String agentSessionId, WebSocketSession session) {
        sessionsByAgentSessionId
                .computeIfAbsent(agentSessionId, ignored -> new CopyOnWriteArrayList<>())
                .add(session);
    }

    public void unregister(String agentSessionId, WebSocketSession session) {
        List<WebSocketSession> sessions = sessionsByAgentSessionId.get(agentSessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByAgentSessionId.remove(agentSessionId);
        }
    }

    public void sendToSession(String agentSessionId, Object payload) {
        List<WebSocketSession> sessions = sessionsByAgentSessionId.get(agentSessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String message;
        try {
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                unregister(agentSessionId, session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                unregister(agentSessionId, session);
            }
        }
    }
}
