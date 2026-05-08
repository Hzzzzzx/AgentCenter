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
                result.addAll(translateMessagePart(opencodeSessionId, agentSessionId, props, eventType, context));
            }
            case "session.status" -> {
                result.addAll(translateSessionStatus(opencodeSessionId, agentSessionId, props, context));
            }
            case "session.idle" -> {
                result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
                    opencodeSessionId, payloadNode("status", "waiting_user")));
                result.add(buildProcessTraceEnvelope(
                    RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                    processTracePayload("node_status", "completed", "状态", "Agent 本轮处理已完成", null, null),
                    context));
                result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                    opencodeSessionId, JsonNodeFactory.instance.objectNode()));
            }
            case "permission.asked", "permission.updated" -> {
                result.addAll(translatePermission(opencodeSessionId, agentSessionId, props, context));
            }
            case "session.error" -> {
                result.addAll(translateSessionError(opencodeSessionId, agentSessionId, props, context));
            }
            default -> {}
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateMessagePart(String opencodeSessionId, String agentSessionId,
                                                            JsonNode properties, String eventType,
                                                            RuntimeTranslationContext context) {
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
                    opencodeSessionId, payloadNode("assistant_delta", text, Map.of("delta", text))));
            }
        } else if ("tool".equals(partType)) {
            result.addAll(translateToolPart(opencodeSessionId, agentSessionId, part, context));
        } else if ("reasoning".equals(partType)) {
            String summary = safeReasoningSummary(part, delta);
            if (!summary.isBlank()) {
                result.add(buildProcessTraceEnvelope(
                    RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                    processTracePayload("reasoning_summary", "running", "思考摘要", summary, null, null),
                    context));
            }
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

    private List<RuntimeEventEnvelope> translateToolPart(String opencodeSessionId, String agentSessionId,
                                                          JsonNode part, RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String callId = part.path("callID").asText(part.path("call_id").asText(part.path("id").asText("tool_" + System.currentTimeMillis())));
        String skillName = part.path("tool").asText(part.path("name").asText("unknown"));
        JsonNode stateNode = part.path("state");
        String status = stateNode.path("status").asText("running");

        if (("running".equals(status) || "completed".equals(status) || "error".equals(status))
                && state.addRunningTool(opencodeSessionId, callId)) {
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_STARTED, agentSessionId,
                opencodeSessionId, payloadNode("skill_started", skillName, Map.of("toolCallId", callId))));
            result.add(buildProcessTraceEnvelope(
                RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                processTracePayload("tool_call", "running", "调用工具", "正在调用 " + skillName, skillName, callId),
                context));
        }

        if ("completed".equals(status) || "error".equals(status)) {
            String output = "error".equals(status)
                    ? stringifyValue(stateNode.path("error").isMissingNode() ? part.path("error") : stateNode.path("error"))
                    : stringifyValue(stateNode.path("output").isMissingNode()
                    ? (stateNode.path("result").isMissingNode() ? part.path("output") : stateNode.path("result"))
                    : stateNode.path("output"));
            boolean isError = "error".equals(status);
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_COMPLETED, agentSessionId,
                opencodeSessionId, payloadNode("skill_completed", skillName, Map.of(
                    "toolCallId", callId, "isError", isError, "output", output))));
            String traceStatus = isError ? "failed" : "completed";
            String traceSummary = isError ? skillName + " 调用失败" : skillName + " 调用完成";
            result.add(buildProcessTraceEnvelope(
                RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                processTracePayload("tool_call", traceStatus, "调用工具", traceSummary, skillName, callId),
                context));
            state.removeRunningTool(opencodeSessionId, callId);
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateSessionStatus(String opencodeSessionId, String agentSessionId,
                                                               JsonNode properties, RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode statusNode = properties.path("status");
        String rawStatus = statusNode.isObject() ? statusNode.path("type").asText("") : statusNode.asText("");
        if (rawStatus.isEmpty()) rawStatus = properties.path("type").asText("unknown");
        String status = "busy".equals(rawStatus) ? "running" : rawStatus;

        result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
            opencodeSessionId, payloadNode("status", status)));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("node_status", traceStatusForRuntimeStatus(status), "状态",
                statusSummary(status), null, null), context));

        if ("idle".equals(status) || "waiting_user".equals(status)) {
            result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                opencodeSessionId, JsonNodeFactory.instance.objectNode()));
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translatePermission(String opencodeSessionId, String agentSessionId,
                                                            JsonNode properties, RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String permissionId = properties.path("id").asText("");
        String permission = properties.path("permission").asText(properties.path("type").asText("opencode_permission"));
        String skillName = properties.path("tool").path("tool").asText(
                properties.path("tool").path("name").asText(permission));
        String title = properties.path("title").asText("OpenCode permission: " + permission);

        result.add(buildEnvelope(RuntimeEventTypes.PERMISSION_REQUESTED, agentSessionId,
            opencodeSessionId, payloadNode("permission_required", skillName, Map.of(
                "permissionId", permissionId,
                "title", title))));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("confirmation", "waiting", "权限确认", title, skillName, permissionId),
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateSessionError(String opencodeSessionId, String agentSessionId,
                                                             JsonNode properties, RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode error = properties.path("error");
        String reason = error.path("data").path("message").asText("");
        if (reason.isEmpty()) reason = error.path("name").asText("");
        if (reason.isEmpty()) reason = properties.path("message").asText("unknown OpenCode session error");

        result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_ERROR, agentSessionId,
            opencodeSessionId, payloadNode("status", "failed", Map.of("reason", reason))));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("error", "failed", "异常", reason, null, null),
            context));
        return result;
    }

    private RuntimeEventEnvelope buildEnvelope(String type, String agentSessionId,
                                                String opencodeSessionId, JsonNode payload) {
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            RuntimeType.OPENCODE, agentSessionId, opencodeSessionId, null, null, null,
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

    private String traceStatusForRuntimeStatus(String status) {
        return switch (status) {
            case "idle", "waiting_user" -> "completed";
            case "failed", "error" -> "failed";
            default -> "running";
        };
    }

    private String statusSummary(String status) {
        return switch (status) {
            case "running", "busy" -> "Agent 正在处理当前请求";
            case "waiting_user" -> "Agent 正在等待用户输入";
            case "idle" -> "Agent 本轮处理已完成";
            case "failed", "error" -> "Agent 运行失败";
            default -> "状态更新：" + status;
        };
    }

    private RuntimeEventEnvelope buildProcessTraceEnvelope(String type, String agentSessionId,
                                                            String opencodeSessionId, JsonNode payload,
                                                            RuntimeTranslationContext context) {
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            RuntimeType.OPENCODE, agentSessionId, opencodeSessionId,
            context.getWorkItemId(agentSessionId),
            context.getWorkflowInstanceId(agentSessionId),
            context.getWorkflowNodeInstanceId(agentSessionId),
            payload, java.time.OffsetDateTime.now());
    }

    private String safeReasoningSummary(JsonNode part, String delta) {
        String visibility = part.path("visibility").asText("");
        boolean isPublicSummary = "public_summary".equals(visibility) || "public".equals(visibility);

        if (isPublicSummary) {
            String summary = part.path("summary").asText("");
            if (!summary.isBlank()) return truncate(summary, 200);
            String text = part.path("text").asText("");
            if (!text.isBlank()) return truncate(text, 200);
        }

        String summary = part.path("summary").asText("");
        if (!summary.isBlank() && summary.length() <= 200) return summary;

        return "正在分析上下文并规划下一步";
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private JsonNode processTracePayload(String kind, String status, String title, String summary,
                                          String toolName, String toolCallId) {
        var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        node.put("kind", kind);
        node.put("status", status);
        node.put("title", title);
        node.put("summary", summary);
        if (toolName != null) node.put("toolName", toolName);
        if (toolCallId != null) node.put("toolCallId", toolCallId);
        node.put("visibility", "public_summary");
        return node;
    }
}
