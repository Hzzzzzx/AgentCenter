package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventTranslator;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationResult;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class OpenCodeRuntimeEventTranslator implements RuntimeEventTranslator {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeRuntimeEventTranslator.class);

    private final OpenCodeTranslationState state;

    public OpenCodeRuntimeEventTranslator(OpenCodeTranslationState state) {
        this.state = state;
    }

    @Override
    public List<RuntimeEventEnvelope> translate(RuntimeRawEvent raw, RuntimeTranslationContext context) {
        String eventType = raw.rawType();
        JsonNode props = extractProperties(raw.rawJson());
        String opencodeSessionId = raw.runtimeSessionId();
        String agentSessionId = context.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || eventType.isEmpty()) return List.of();

        List<RuntimeEventEnvelope> result = new ArrayList<>();

        switch (eventType) {
            case "message.updated" -> {
                JsonNode info = props.path("info");
                if ("user".equals(info.path("role").asText("")) && info.has("id")) {
                    state.recordUserMessageId(opencodeSessionId, info.path("id").asText());
                    context.recordUserMessageId(opencodeSessionId, info.path("id").asText());
                }
            }
            case "message.part.updated", "message.part.delta" -> {
                result.addAll(translateMessagePart(opencodeSessionId, agentSessionId, props, eventType));
            }
            case "session.status" -> {
                result.addAll(translateSessionStatus(agentSessionId, props));
            }
            case "session.idle" -> {
                result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
                    payloadNode("status", "waiting_user")));
                result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                    JsonNodeFactory.instance.objectNode()));
            }
            case "permission.asked", "permission.updated" -> {
                result.addAll(translatePermission(agentSessionId, props));
            }
            case "session.error" -> {
                result.addAll(translateSessionError(agentSessionId, props));
            }
            default -> {}
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateMessagePart(String opencodeSessionId, String agentSessionId,
                                                            JsonNode properties, String eventType) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode part = properties.path("part");
        if (part.isMissingNode()) part = properties;

        String msgId = part.path("messageID").asText(part.path("message_id").asText(""));
        if (!msgId.isEmpty() && state.isUserMessage(opencodeSessionId, msgId)) return result;

        String delta = properties.path("delta").asText("");
        String partType = part.path("type").asText("");

        if ("text".equals(partType)) {
            String text = resolvePartText(opencodeSessionId, part, delta);
            if (!text.isEmpty()) {
                result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_DELTA, agentSessionId,
                    payloadNode("assistant_delta", text)));
            }
        } else if ("tool".equals(partType)) {
            result.addAll(translateToolPart(opencodeSessionId, agentSessionId, part));
        }

        return result;
    }

    private String resolvePartText(String opencodeSessionId, JsonNode part, String delta) {
        String partId = part.path("id").asText("");
        if (!delta.isEmpty()) {
            if (!partId.isEmpty()) state.markSeenTextPart(opencodeSessionId, partId);
            return delta;
        }
        String partText = part.path("text").asText("");
        if (partText.isEmpty()) return "";
        if (partId.isEmpty()) return partText;
        if (state.isSeenTextPart(opencodeSessionId, partId)) return "";
        state.markSeenTextPart(opencodeSessionId, partId);
        return partText;
    }

    private List<RuntimeEventEnvelope> translateToolPart(String opencodeSessionId, String agentSessionId, JsonNode part) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String callId = part.path("callID").asText(part.path("call_id").asText(part.path("id").asText("tool_" + System.currentTimeMillis())));
        String skillName = part.path("tool").asText(part.path("name").asText("unknown"));
        JsonNode stateNode = part.path("state");
        String status = stateNode.path("status").asText("running");

        if (("running".equals(status) || "completed".equals(status) || "error".equals(status))
                && state.addRunningTool(opencodeSessionId, callId)) {
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_STARTED, agentSessionId,
                payloadNode("skill_started", skillName, Map.of("toolCallId", callId))));
        }

        if ("completed".equals(status) || "error".equals(status)) {
            String output = "error".equals(status)
                    ? stringifyValue(stateNode.path("error").isMissingNode() ? part.path("error") : stateNode.path("error"))
                    : stringifyValue(stateNode.path("output").isMissingNode()
                    ? (stateNode.path("result").isMissingNode() ? part.path("output") : stateNode.path("result"))
                    : stateNode.path("output"));
            boolean isError = "error".equals(status);
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_COMPLETED, agentSessionId,
                payloadNode("skill_completed", skillName, Map.of(
                    "toolCallId", callId, "isError", isError, "output", output))));
            state.removeRunningTool(opencodeSessionId, callId);
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateSessionStatus(String agentSessionId, JsonNode properties) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode statusNode = properties.path("status");
        String rawStatus = statusNode.isObject() ? statusNode.path("type").asText("") : statusNode.asText("");
        if (rawStatus.isEmpty()) rawStatus = properties.path("type").asText("unknown");
        String status = "busy".equals(rawStatus) ? "running" : rawStatus;

        result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
            payloadNode("status", status)));

        if ("idle".equals(status) || "waiting_user".equals(status)) {
            result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                JsonNodeFactory.instance.objectNode()));
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translatePermission(String agentSessionId, JsonNode properties) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String permissionId = properties.path("id").asText("");
        String permission = properties.path("permission").asText(properties.path("type").asText("opencode_permission"));
        String skillName = properties.path("tool").path("tool").asText(
                properties.path("tool").path("name").asText(permission));

        result.add(buildEnvelope(RuntimeEventTypes.PERMISSION_REQUESTED, agentSessionId,
            payloadNode("permission_required", skillName, Map.of(
                "permissionId", permissionId,
                "title", properties.path("title").asText("OpenCode permission: " + permission)))));
        return result;
    }

    private List<RuntimeEventEnvelope> translateSessionError(String agentSessionId, JsonNode properties) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode error = properties.path("error");
        String reason = error.path("data").path("message").asText("");
        if (reason.isEmpty()) reason = error.path("name").asText("");
        if (reason.isEmpty()) reason = properties.path("message").asText("unknown OpenCode session error");

        result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_ERROR, agentSessionId,
            payloadNode("status", "failed", Map.of("reason", reason))));
        return result;
    }

    private RuntimeEventEnvelope buildEnvelope(String type, String agentSessionId, JsonNode payload) {
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            RuntimeType.OPENCODE, agentSessionId, null, null, null, null,
            payload, java.time.OffsetDateTime.now());
    }

    private JsonNode extractProperties(JsonNode raw) {
        if (raw.has("properties")) return raw.get("properties");
        if (raw.has("data")) return raw.get("data");
        return raw;
    }

    private JsonNode payloadNode(String type, Object label) {
        return payloadNode(type, label, Map.of());
    }

    private JsonNode payloadNode(String type, Object label, Map<String, Object> extra) {
        var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        node.put("type", type);
        if (label instanceof String s) node.put("label", s);
        else if (label instanceof Boolean b) node.put("label", b);
        else if (label instanceof Number n) node.put("label", n.doubleValue());
        for (var entry : extra.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof String s) node.put(entry.getKey(), s);
            else if (v instanceof Boolean b) node.put(entry.getKey(), b);
            else if (v instanceof Number n) node.put(entry.getKey(), n.doubleValue());
        }
        return node;
    }

    private String stringifyValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        return value.isTextual() ? value.asText() : value.toString();
    }
}
