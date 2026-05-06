package com.agentcenter.bridge.api.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.agentcenter.bridge.api.dto.SendMessageRequest;
import com.agentcenter.bridge.application.AgentSessionService;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SessionWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_ID_ATTR = "agentSessionId";

    private final ObjectMapper objectMapper;
    private final AgentSessionService sessionService;
    private final RuntimeEventService eventService;
    private final WebSocketSessionRegistry sessionRegistry;

    public SessionWebSocketHandler(ObjectMapper objectMapper,
                                   AgentSessionService sessionService,
                                   RuntimeEventService eventService,
                                   WebSocketSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.eventService = eventService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String agentSessionId = extractSessionId(session);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        session.getAttributes().put(SESSION_ID_ATTR, agentSessionId);
        sessionRegistry.register(agentSessionId, session);
        send(session, envelope("session.connected", Map.of("sessionId", agentSessionId)));
        sendMessagesSnapshot(session, agentSessionId);
        send(session, envelope("runtime.events", Map.of(
                "events", eventService.getEventsBySession(agentSessionId)
        )));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String agentSessionId = (String) session.getAttributes().get(SESSION_ID_ATTR);
        if (agentSessionId == null) {
            send(session, envelope("error", Map.of("message", "Session is not bound")));
            return;
        }

        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();

        switch (type) {
            case "user.message" -> handleUserMessage(session, agentSessionId, root.path("payload"));
            case "session.reload" -> {
                sendMessagesSnapshot(session, agentSessionId);
                send(session, envelope("runtime.events", Map.of(
                        "events", eventService.getEventsBySession(agentSessionId)
                )));
            }
            case "ping" -> send(session, envelope("pong", Map.of("sessionId", agentSessionId)));
            default -> send(session, envelope("error", Map.of("message", "Unsupported event type: " + type)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentSessionId = (String) session.getAttributes().get(SESSION_ID_ATTR);
        if (agentSessionId != null) {
            sessionRegistry.unregister(agentSessionId, session);
        }
    }

    private void handleUserMessage(WebSocketSession session, String agentSessionId, JsonNode payload) throws IOException {
        String content = payload.path("content").asText();
        if (content == null || content.isBlank()) {
            send(session, envelope("error", Map.of("message", "Message content is required")));
            return;
        }

        ContentFormat contentFormat = parseContentFormat(payload.path("contentFormat").asText("TEXT"));
        var created = sessionService.sendMessage(agentSessionId, new SendMessageRequest(content, contentFormat));
        sessionRegistry.sendToSession(agentSessionId, envelope("message.created", Map.of("message", created)));
        sessionRegistry.sendToSession(agentSessionId, messagesEnvelope(agentSessionId));
    }

    private ContentFormat parseContentFormat(String value) {
        try {
            return ContentFormat.valueOf(value);
        } catch (Exception e) {
            return ContentFormat.TEXT;
        }
    }

    private void sendMessagesSnapshot(WebSocketSession session, String agentSessionId) throws IOException {
        send(session, messagesEnvelope(agentSessionId));
    }

    private Map<String, Object> messagesEnvelope(String agentSessionId) {
        return envelope("session.messages", Map.of(
                "sessionId", agentSessionId,
                "messages", sessionService.getMessages(agentSessionId)
        ));
    }

    private Map<String, Object> envelope(String type, Object payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", type);
        envelope.put("payload", payload);
        return envelope;
    }

    private void send(WebSocketSession session, Map<String, Object> envelope) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        }
    }

    private String extractSessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        String prefix = "/ws/agent-sessions/";
        int index = path.indexOf(prefix);
        if (index < 0) {
            return null;
        }
        return path.substring(index + prefix.length());
    }
}
