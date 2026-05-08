package com.agentcenter.bridge.application.runtime.transport.websocket;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventStreamTransport;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory fake runtime that wires command transport to event stream transport.
 * No network — proves the SPI contract works end-to-end.
 */
public class FakeWebSocketRuntime implements RuntimeCommandTransport, RuntimeEventStreamTransport {

    private final RuntimeType runtimeType;
    private final List<RuntimeEventSink> sinks = new CopyOnWriteArrayList<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public FakeWebSocketRuntime(RuntimeType runtimeType) {
        this.runtimeType = runtimeType;
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command) {
        return send(command, Duration.ofSeconds(30));
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout) {
        return switch (command.type()) {
            case RuntimeCommandTypes.SESSION_ENSURE -> handleSessionEnsure(command);
            case RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND -> handleConversationMessage(command);
            case RuntimeCommandTypes.CONVERSATION_CANCEL -> handleConversationCancel(command);
            case RuntimeCommandTypes.SKILL_INSTALL -> handleSkillInstall(command);
            case RuntimeCommandTypes.SKILL_DELETE -> handleSkillDelete(command);
            case RuntimeCommandTypes.SKILL_RUN -> handleSkillRun(command);
            case RuntimeCommandTypes.MCP_REFRESH -> handleMcpRefresh(command);
            default -> RuntimeAckEnvelope.nack(command.messageId(), runtimeType,
                "Unknown command type: " + command.type());
        };
    }

    @Override
    public SubscriptionHandle subscribe(RuntimeEventSink sink) {
        sinks.add(sink);
        return new FakeSubscriptionHandle(sink, sinks);
    }

    private RuntimeAckEnvelope handleSessionEnsure(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId() != null
            ? command.runtimeSessionId()
            : java.util.UUID.randomUUID().toString();
        sessions.put(sessionId, "active");

        emitEvent(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("status", "active")
                .put("sessionId", sessionId));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleConversationMessage(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();
        String text = extractText(command.payload());

        emitEvent(RuntimeEventTypes.CONVERSATION_DELTA, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("text", "Echo: " + text));

        emitEvent(RuntimeEventTypes.CONVERSATION_COMPLETED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("reason", "completed"));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleConversationCancel(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();

        emitEvent(RuntimeEventTypes.RUNTIME_ERROR, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("error", "conversation_cancelled")
                .put("message", "Conversation was cancelled"));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleSkillInstall(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();
        String skillName = extractField(command.payload(), "skillName");

        emitEvent(RuntimeEventTypes.SKILL_INSTALL_COMPLETED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("skillName", skillName)
                .put("status", "installed"));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleSkillDelete(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();
        String skillName = extractField(command.payload(), "skillName");

        emitEvent(RuntimeEventTypes.SKILL_DELETE_COMPLETED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("skillName", skillName)
                .put("status", "deleted"));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleSkillRun(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();
        String skillName = extractField(command.payload(), "skillName");

        emitEvent(RuntimeEventTypes.SKILL_RUN_STARTED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("skillName", skillName)
                .put("status", "started"));

        emitEvent(RuntimeEventTypes.SKILL_RUN_COMPLETED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("skillName", skillName)
                .put("status", "completed")
                .put("output", "Skill '" + skillName + "' executed successfully"));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private RuntimeAckEnvelope handleMcpRefresh(RuntimeCommandEnvelope command) {
        String sessionId = command.runtimeSessionId();

        emitEvent(RuntimeEventTypes.MCP_REFRESH_COMPLETED, sessionId,
            JsonNodeFactory.instance.objectNode()
                .put("status", "refreshed")
                .put("serverCount", 0));

        return RuntimeAckEnvelope.ack(command.messageId(), runtimeType);
    }

    private void emitEvent(String eventType, String sessionId, JsonNode payload) {
        var event = new RuntimeRawEvent(runtimeType, eventType, payload, sessionId);
        for (RuntimeEventSink sink : sinks) {
            sink.onEvent(event);
        }
    }

    private String extractText(JsonNode payload) {
        if (payload != null && payload.has("text")) {
            return payload.get("text").asText();
        }
        return "";
    }

    private String extractField(JsonNode payload, String fieldName) {
        if (payload != null && payload.has(fieldName)) {
            return payload.get(fieldName).asText();
        }
        return "";
    }

    int activeSinkCount() {
        return sinks.size();
    }

    private static class FakeSubscriptionHandle implements SubscriptionHandle {
        private volatile boolean active = true;
        private final RuntimeEventSink sink;
        private final List<RuntimeEventSink> sinkList;

        FakeSubscriptionHandle(RuntimeEventSink sink, List<RuntimeEventSink> sinkList) {
            this.sink = sink;
            this.sinkList = sinkList;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            sinkList.remove(sink);
            sink.onClose();
        }
    }
}
