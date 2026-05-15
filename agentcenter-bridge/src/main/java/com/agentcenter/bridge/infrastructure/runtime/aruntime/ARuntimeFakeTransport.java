package com.agentcenter.bridge.infrastructure.runtime.aruntime;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEnvelopeKind;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventStreamTransport;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Local fake for validating the A Runtime adapter contract without reaching an enterprise network.
 */
public class ARuntimeFakeTransport implements RuntimeCommandTransport, RuntimeEventStreamTransport {

    private final List<RuntimeEventSink> sinks = new CopyOnWriteArrayList<>();

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command) {
        return send(command, Duration.ofSeconds(30));
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout) {
        return switch (command.type()) {
            case RuntimeCommandTypes.SESSION_ENSURE -> ensureSession(command);
            case RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND -> sendMessage(command);
            case RuntimeCommandTypes.SKILL_RUN -> runSkill(command);
            case RuntimeCommandTypes.CONVERSATION_CANCEL -> cancel(command);
            default -> nack(command, "A Runtime fake does not support command: " + command.type());
        };
    }

    @Override
    public SubscriptionHandle subscribe(RuntimeEventSink sink) {
        sinks.add(sink);
        return new Handle(sink);
    }

    private RuntimeAckEnvelope ensureSession(RuntimeCommandEnvelope command) {
        String sessionId = nonBlank(command.runtimeSessionId(), "arun_" + UUID.randomUUID());
        ObjectNode payload = JsonNodeFactory.instance.objectNode()
                .put("sessionId", sessionId)
                .put("status", "ready");
        emit(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, command, sessionId,
                JsonNodeFactory.instance.objectNode()
                        .put("status", "ready")
                        .put("sessionId", sessionId));
        return ack(command, sessionId, payload);
    }

    private RuntimeAckEnvelope sendMessage(RuntimeCommandEnvelope command) {
        String sessionId = requireSession(command);
        String text = command.payload().path("text").asText("");
        emit(RuntimeEventTypes.CONVERSATION_DELTA, command, sessionId,
                JsonNodeFactory.instance.objectNode().put("text", "A Runtime echo: " + text));
        emit(RuntimeEventTypes.CONVERSATION_COMPLETED, command, sessionId,
                JsonNodeFactory.instance.objectNode().put("reason", "completed"));
        return ack(command, sessionId, JsonNodeFactory.instance.objectNode().put("accepted", true));
    }

    private RuntimeAckEnvelope runSkill(RuntimeCommandEnvelope command) {
        String sessionId = requireSession(command);
        String skillName = command.payload().path("skillName").asText("unknown");
        String output = "A Runtime skill '" + skillName + "' completed";
        emit(RuntimeEventTypes.SKILL_RUN_STARTED, command, sessionId,
                JsonNodeFactory.instance.objectNode().put("skillName", skillName));
        emit(RuntimeEventTypes.SKILL_RUN_COMPLETED, command, sessionId,
                JsonNodeFactory.instance.objectNode()
                        .put("skillName", skillName)
                        .put("output", output));
        return ack(command, sessionId, JsonNodeFactory.instance.objectNode().put("output", output));
    }

    private RuntimeAckEnvelope cancel(RuntimeCommandEnvelope command) {
        String sessionId = requireSession(command);
        emit(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, command, sessionId,
                JsonNodeFactory.instance.objectNode()
                        .put("status", "cancelled")
                        .put("sessionId", sessionId));
        return ack(command, sessionId, JsonNodeFactory.instance.objectNode().put("cancelled", true));
    }

    private String requireSession(RuntimeCommandEnvelope command) {
        if (command.runtimeSessionId() == null || command.runtimeSessionId().isBlank()) {
            throw new IllegalArgumentException("A Runtime command requires runtimeSessionId: " + command.type());
        }
        return command.runtimeSessionId();
    }

    private RuntimeAckEnvelope ack(RuntimeCommandEnvelope command, String runtimeSessionId, JsonNode payload) {
        return new RuntimeAckEnvelope(RuntimeEnvelopeKind.ACK, "agentcenter.runtime.v1", null,
                UUID.randomUUID().toString(), command.messageId(), command.operationId(),
                RuntimeType.A_RUNTIME, command.agentSessionId(), runtimeSessionId,
                true, null, payload, OffsetDateTime.now());
    }

    private RuntimeAckEnvelope nack(RuntimeCommandEnvelope command, String message) {
        return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.A_RUNTIME, message,
                RuntimeOperationContext.empty()
                        .withOperationId(command.operationId())
                        .withAgentSessionId(command.agentSessionId())
                        .withRuntimeSessionId(command.runtimeSessionId()));
    }

    private void emit(String eventType, RuntimeCommandEnvelope command, String runtimeSessionId, JsonNode payload) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("operationId", nonBlank(command.operationId(), ""));
        root.put("agentSessionId", nonBlank(command.agentSessionId(), ""));
        root.put("workItemId", nonBlank(command.workItemId(), ""));
        root.put("workflowInstanceId", nonBlank(command.workflowInstanceId(), ""));
        root.put("workflowNodeInstanceId", nonBlank(command.workflowNodeInstanceId(), ""));
        root.set("payload", payload);
        RuntimeRawEvent event = new RuntimeRawEvent(RuntimeType.A_RUNTIME, eventType, root, runtimeSessionId);
        for (RuntimeEventSink sink : sinks) {
            sink.onEvent(event);
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private final class Handle implements SubscriptionHandle {
        private final RuntimeEventSink sink;
        private volatile boolean active = true;

        private Handle(RuntimeEventSink sink) {
            this.sink = sink;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            sinks.remove(sink);
            sink.onClose();
        }
    }
}
