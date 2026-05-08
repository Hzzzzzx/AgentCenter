package com.agentcenter.bridge.application.runtime.protocol;

import java.time.OffsetDateTime;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeCommandEnvelope(
    RuntimeEnvelopeKind kind,
    String protocol,
    String type,
    String messageId,
    String correlationId,
    String operationId,
    String idempotencyKey,
    RuntimeType runtimeType,
    String agentSessionId,
    String runtimeSessionId,
    String projectId,
    String workItemId,
    String workflowInstanceId,
    String workflowNodeInstanceId,
    JsonNode payload,
    OffsetDateTime createdAt
) {
    public static RuntimeCommandEnvelope of(String type, RuntimeType runtimeType, String runtimeSessionId, JsonNode payload) {
        return new RuntimeCommandEnvelope(
            RuntimeEnvelopeKind.COMMAND, "agentcenter.runtime.v1", type,
            java.util.UUID.randomUUID().toString(), null, null, null,
            runtimeType, null, runtimeSessionId, null, null, null, null,
            payload, OffsetDateTime.now());
    }
}
