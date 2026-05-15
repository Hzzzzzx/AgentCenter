package com.agentcenter.bridge.application.runtime.protocol;

import java.time.OffsetDateTime;
import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
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
        return ack(correlationId, runtimeType, RuntimeOperationContext.empty());
    }

    public static RuntimeAckEnvelope ack(String correlationId, RuntimeType runtimeType, RuntimeOperationContext context) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        return new RuntimeAckEnvelope(RuntimeEnvelopeKind.ACK, "agentcenter.runtime.v1", null,
            java.util.UUID.randomUUID().toString(), correlationId, ctx.operationId(), runtimeType,
            ctx.agentSessionId(), ctx.runtimeSessionId(),
            true, null, null, OffsetDateTime.now());
    }

    public static RuntimeAckEnvelope nack(String correlationId, RuntimeType runtimeType, String message) {
        return nack(correlationId, runtimeType, message, RuntimeOperationContext.empty());
    }

    public static RuntimeAckEnvelope nack(String correlationId, RuntimeType runtimeType, String message,
                                          RuntimeOperationContext context) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        return new RuntimeAckEnvelope(RuntimeEnvelopeKind.NACK, "agentcenter.runtime.v1", null,
            java.util.UUID.randomUUID().toString(), correlationId, ctx.operationId(), runtimeType,
            ctx.agentSessionId(), ctx.runtimeSessionId(),
            false, message, null, OffsetDateTime.now());
    }
}
