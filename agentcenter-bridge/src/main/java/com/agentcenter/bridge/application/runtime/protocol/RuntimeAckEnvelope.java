package com.agentcenter.bridge.application.runtime.protocol;

import java.time.OffsetDateTime;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeAckEnvelope(
    RuntimeEnvelopeKind kind,
    String protocol,
    String type,
    String messageId,
    String correlationId,
    String operationId,
    RuntimeType runtimeType,
    String agentSessionId,
    String runtimeSessionId,
    boolean success,
    String message,
    JsonNode payload,
    OffsetDateTime createdAt
) {
    public static RuntimeAckEnvelope ack(String correlationId, RuntimeType runtimeType) {
        return new RuntimeAckEnvelope(RuntimeEnvelopeKind.ACK, "agentcenter.runtime.v1", null,
            java.util.UUID.randomUUID().toString(), correlationId, null, runtimeType, null, null,
            true, null, null, OffsetDateTime.now());
    }

    public static RuntimeAckEnvelope nack(String correlationId, RuntimeType runtimeType, String message) {
        return new RuntimeAckEnvelope(RuntimeEnvelopeKind.NACK, "agentcenter.runtime.v1", null,
            java.util.UUID.randomUUID().toString(), correlationId, null, runtimeType, null, null,
            false, message, null, OffsetDateTime.now());
    }
}
