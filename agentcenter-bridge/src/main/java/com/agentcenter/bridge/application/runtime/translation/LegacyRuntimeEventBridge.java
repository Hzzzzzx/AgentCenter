package com.agentcenter.bridge.application.runtime.translation;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class LegacyRuntimeEventBridge {

    public RuntimeEventDto toLegacyEvent(RuntimeEventEnvelope envelope) {
        RuntimeEventType legacyType = mapType(envelope.type());
        if (legacyType == null) return null;

        String payloadJson = buildPayloadJson(envelope);

        return new RuntimeEventDto(
            null,
            envelope.agentSessionId(),
            envelope.workItemId(),
            envelope.workflowInstanceId(),
            envelope.workflowNodeInstanceId(),
            legacyType,
            RuntimeEventSource.OPENCODE,
            payloadJson,
            envelope.occurredAt() != null ? envelope.occurredAt() : OffsetDateTime.now()
        );
    }

    String buildPayloadJson(RuntimeEventEnvelope envelope) {
        ObjectNode root;
        if (envelope.payload() != null && envelope.payload().isObject()) {
            root = ((ObjectNode) envelope.payload()).deepCopy();
        } else {
            root = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        }
        if (envelope.messageId() != null && !root.has("messageId")) {
            root.put("messageId", envelope.messageId());
        }
        if (envelope.correlationId() != null && !root.has("correlationId")) {
            root.put("correlationId", envelope.correlationId());
        }
        if (envelope.operationId() != null && !root.has("operationId")) {
            root.put("operationId", envelope.operationId());
        }
        String toolCallId = textValue(envelope.payload(), "toolCallId");
        if (toolCallId != null && !root.has("toolCallId")) {
            root.put("toolCallId", toolCallId);
        }
        String partId = textValue(envelope.payload(), "partId");
        if (partId != null && !root.has("partId")) {
            root.put("partId", partId);
        }
        String confirmationId = textValue(envelope.payload(), "confirmationId");
        if (confirmationId != null && !root.has("confirmationId")) {
            root.put("confirmationId", confirmationId);
        }
        String artifactId = textValue(envelope.payload(), "artifactId");
        if (artifactId != null && !root.has("artifactId")) {
            root.put("artifactId", artifactId);
        }
        String filePath = textValue(envelope.payload(), "filePath");
        if (filePath != null && !root.has("filePath")) {
            root.put("filePath", filePath);
        }
        String rawEventType = textValue(envelope.payload(), "rawEventType");
        if (rawEventType != null && !root.has("rawEventType")) {
            root.put("rawEventType", rawEventType);
        }
        String rawPartType = textValue(envelope.payload(), "rawPartType");
        if (rawPartType != null && !root.has("rawPartType")) {
            root.put("rawPartType", rawPartType);
        }
        String parentStepId = textValue(envelope.payload(), "parentStepId");
        if (parentStepId != null && !root.has("parentStepId")) {
            root.put("parentStepId", parentStepId);
        }
        return root.toString();
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v.isNull() ? null : v.asText();
    }

    private RuntimeEventType mapType(String unifiedType) {
        return switch (unifiedType) {
            case RuntimeEventTypes.CONVERSATION_DELTA -> RuntimeEventType.ASSISTANT_DELTA;
            case RuntimeEventTypes.CONVERSATION_COMPLETED -> RuntimeEventType.ASSISTANT_COMPLETED;
            case RuntimeEventTypes.TOOL_STARTED -> RuntimeEventType.SKILL_STARTED;
            case RuntimeEventTypes.TOOL_COMPLETED -> RuntimeEventType.SKILL_COMPLETED;
            case RuntimeEventTypes.PERMISSION_REQUESTED -> RuntimeEventType.PERMISSION_REQUIRED;
            case RuntimeEventTypes.RUNTIME_STATUS_CHANGED -> RuntimeEventType.STATUS;
            case RuntimeEventTypes.RUNTIME_ERROR -> RuntimeEventType.ERROR;
            case RuntimeEventTypes.PROCESS_TRACE -> RuntimeEventType.PROCESS_TRACE;
            default -> null;
        };
    }
}
