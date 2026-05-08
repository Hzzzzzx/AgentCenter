package com.agentcenter.bridge.application.runtime.protocol;

import java.time.OffsetDateTime;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeEventEnvelope(
    String protocol,
    String type,
    String messageId,
    String correlationId,
    String operationId,
    RuntimeType runtimeType,
    String agentSessionId,
    String runtimeSessionId,
    String workItemId,
    String workflowInstanceId,
    String workflowNodeInstanceId,
    JsonNode payload,
    OffsetDateTime occurredAt
) {
    public static RuntimeEventEnvelope of(String type, RuntimeType runtimeType, String agentSessionId, JsonNode payload) {
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            runtimeType, agentSessionId, null, null, null, null,
            payload, OffsetDateTime.now());
    }

    public static RuntimeEventEnvelope of(String type, RuntimeType runtimeType, String agentSessionId,
                                           String runtimeSessionId, JsonNode payload) {
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            runtimeType, agentSessionId, runtimeSessionId, null, null, null,
            payload, OffsetDateTime.now());
    }
}
