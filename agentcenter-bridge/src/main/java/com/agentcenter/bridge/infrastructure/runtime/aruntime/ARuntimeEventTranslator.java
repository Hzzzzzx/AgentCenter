package com.agentcenter.bridge.infrastructure.runtime.aruntime;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventTranslator;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Translates A Runtime raw events into AgentCenter runtime events.
 */
public class ARuntimeEventTranslator implements RuntimeEventTranslator {

    @Override
    public List<RuntimeEventEnvelope> translate(RuntimeRawEvent raw, RuntimeTranslationContext context) {
        String runtimeSessionId = raw.runtimeSessionId();
        JsonNode root = raw.rawJson();
        String agentSessionId = text(root, "agentSessionId");
        if (isBlank(agentSessionId) && context != null) {
            agentSessionId = context.getAgentSessionId(runtimeSessionId);
        }
        JsonNode payload = root != null && root.has("payload") ? root.get("payload") : root;

        return List.of(new RuntimeEventEnvelope(
                "agentcenter.runtime.v1",
                raw.rawType(),
                UUID.randomUUID().toString(),
                null,
                text(root, "operationId"),
                RuntimeType.A_RUNTIME,
                agentSessionId,
                runtimeSessionId,
                firstNonBlank(text(root, "workItemId"), contextWorkItemId(context, agentSessionId)),
                firstNonBlank(text(root, "workflowInstanceId"), contextWorkflowInstanceId(context, agentSessionId)),
                firstNonBlank(text(root, "workflowNodeInstanceId"), contextWorkflowNodeInstanceId(context, agentSessionId)),
                payload,
                OffsetDateTime.now()));
    }

    private String contextWorkItemId(RuntimeTranslationContext context, String agentSessionId) {
        if (context == null || isBlank(agentSessionId)) {
            return null;
        }
        return context.getWorkItemId(agentSessionId);
    }

    private String contextWorkflowInstanceId(RuntimeTranslationContext context, String agentSessionId) {
        if (context == null || isBlank(agentSessionId)) {
            return null;
        }
        return context.getWorkflowInstanceId(agentSessionId);
    }

    private String contextWorkflowNodeInstanceId(RuntimeTranslationContext context, String agentSessionId) {
        if (context == null || isBlank(agentSessionId)) {
            return null;
        }
        return context.getWorkflowNodeInstanceId(agentSessionId);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
