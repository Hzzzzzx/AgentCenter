package com.agentcenter.bridge.application.runtime.protocol;

import java.time.OffsetDateTime;
import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
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
        return of(type, runtimeType, runtimeSessionId, payload,
                RuntimeOperationContext.empty().withRuntimeSessionId(runtimeSessionId));
    }

    public static RuntimeCommandEnvelope of(String type, RuntimeType runtimeType, String runtimeSessionId,
                                            JsonNode payload, RuntimeOperationContext context) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        String messageId = isBlank(ctx.messageId()) ? java.util.UUID.randomUUID().toString() : ctx.messageId();
        String commandRuntimeSessionId = isBlank(runtimeSessionId) ? ctx.runtimeSessionId() : runtimeSessionId;
        return new RuntimeCommandEnvelope(
            RuntimeEnvelopeKind.COMMAND, "agentcenter.runtime.v1", type,
            messageId, ctx.correlationId(), ctx.operationId(), ctx.idempotencyKey(),
            runtimeType, ctx.agentSessionId(), commandRuntimeSessionId, ctx.projectId(), ctx.workItemId(),
            ctx.workflowInstanceId(), ctx.workflowNodeInstanceId(),
            payload, OffsetDateTime.now());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
