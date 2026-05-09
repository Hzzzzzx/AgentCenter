package com.agentcenter.bridge.application.runtime.translation;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;

@Component
public class LegacyRuntimeEventBridge {

    public RuntimeEventDto toLegacyEvent(RuntimeEventEnvelope envelope) {
        RuntimeEventType legacyType = mapType(envelope.type());
        if (legacyType == null) return null;

        String payloadJson = envelope.payload() != null ? envelope.payload().toString() : "{}";

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
