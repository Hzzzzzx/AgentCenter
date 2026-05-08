package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;

public record RuntimeEventDto(
        String id,
        String sessionId,
        String workItemId,
        String workflowInstanceId,
        String workflowNodeInstanceId,
        RuntimeEventType eventType,
        RuntimeEventSource eventSource,
        String payloadJson,
        Integer seqNo,
        OffsetDateTime createdAt
) {
    public RuntimeEventDto(
            String id,
            String sessionId,
            String workItemId,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            RuntimeEventType eventType,
            RuntimeEventSource eventSource,
            String payloadJson,
            OffsetDateTime createdAt
    ) {
        this(id, sessionId, workItemId, workflowInstanceId, workflowNodeInstanceId,
                eventType, eventSource, payloadJson, null, createdAt);
    }
}
