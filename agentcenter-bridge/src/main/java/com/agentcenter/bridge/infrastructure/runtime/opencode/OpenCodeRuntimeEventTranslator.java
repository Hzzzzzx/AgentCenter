package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.agentcenter.bridge.application.runtime.translation.QuestionConfirmationHandler;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventTranslator;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationResult;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
            case "message.part.removed" -> {
                result.addAll(translateMessagePartRemoved(opencodeSessionId, agentSessionId, props, context));
            }
            case "session.status" -> {
                result.addAll(translateSessionStatus(opencodeSessionId, agentSessionId, props, context));
            }
            case "session.idle" -> {
                result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
                    opencodeSessionId, payloadNode("status", "waiting_user",
                    projectionMeta("session.idle", null, Map.of()))));
                result.add(buildProcessTraceEnvelope(
                    RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                    processTracePayload("node_status", "completed", "状态", "Agent 本轮处理已完成", null, null,
                        "session.idle", null, Map.of()),
                    context));
                result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                    opencodeSessionId, payloadNode("_completed", true,
                    projectionMeta("session.idle", null, Map.of()))));
            }
            case "permission.asked", "permission.updated" -> {
                result.addAll(translatePermission(opencodeSessionId, agentSessionId, props, context));
            }
            case "permission.replied" -> {
                result.addAll(translatePermissionClosed(opencodeSessionId, agentSessionId, props, context));
            }
            case "question.asked" -> {
                result.addAll(translateQuestionAsked(opencodeSessionId, agentSessionId, props, context));
            }
            case "question.replied", "question.rejected" -> {
                result.addAll(translateQuestionClosed(opencodeSessionId, agentSessionId, props, eventType, context));
            }
            case "session.error" -> {
                result.addAll(translateSessionError(opencodeSessionId, agentSessionId, props, context));
            }
            default -> {}
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateQuestionAsked(String opencodeSessionId, String agentSessionId,
                                                               JsonNode properties, RuntimeTranslationContext context) {
        String requestId = selectQuestionRequestId(
                properties.path("requestID").asText(""),
                properties.path("requestId").asText(""),
                properties.path("id").asText(""));
        JsonNode questions = properties.path("questions");
        JsonNode tool = properties.path("tool");
        String toolCallId = firstNonBlank(tool.path("callID").asText(""), tool.path("call_id").asText(""));
        String messageId = firstNonBlank(tool.path("messageID").asText(""), tool.path("message_id").asText(""));
        return buildQuestionRequestedEnvelopes(opencodeSessionId, agentSessionId, requestId, questions,
                toolCallId, messageId, "question.asked", null, context);
    }

    private List<RuntimeEventEnvelope> buildQuestionRequestedEnvelopes(String opencodeSessionId, String agentSessionId,
                                                                       String requestId, JsonNode questions,
                                                                       String toolCallId, String messageId,
                                                                       String rawEventType, String rawPartType,
                                                                       RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        if (requestId == null || requestId.isBlank()) {
            return result;
        }
        if (!state.addQuestionRequest(opencodeSessionId, requestId, toolCallId)) {
            return result;
        }
        JsonNode firstQuestion = questions.isArray() && !questions.isEmpty()
                ? questions.get(0)
                : JsonNodeFactory.instance.objectNode();
        String title = firstNonBlank(firstQuestion.path("header").asText(""),
                firstQuestion.path("question").asText(""), "OpenCode question");
        String confirmationId = QuestionConfirmationHandler.confirmationIdFor(opencodeSessionId, requestId);

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("type", "question_required");
        payload.put("label", title);
        payload.put("requestId", requestId);
        payload.put("confirmationId", confirmationId);
        payload.put("rawEventType", rawEventType);
        if (rawPartType != null && !rawPartType.isBlank()) payload.put("rawPartType", rawPartType);
        payload.put("skillName", "question");
        if (!toolCallId.isBlank()) payload.put("toolCallId", toolCallId);
        if (!messageId.isBlank()) payload.put("messageId", messageId);
        payload.set("questions", questions.isArray() ? questions.deepCopy() : JsonNodeFactory.instance.arrayNode());

        result.add(buildContextEnvelope(RuntimeEventTypes.QUESTION_REQUESTED, agentSessionId,
                opencodeSessionId, payload, context));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("confirmation", "waiting", "用户问题", title, "question", requestId,
                rawEventType, rawPartType, Map.of("confirmationId", confirmationId)),
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateQuestionClosed(String opencodeSessionId, String agentSessionId,
                                                               JsonNode properties, String eventType,
                                                               RuntimeTranslationContext context) {
        String requestId = properties.path("requestID").asText(properties.path("requestId").asText(""));
        String status = "question.rejected".equals(eventType) ? "failed" : "completed";
        String summary = "question.rejected".equals(eventType) ? "Question 已取消" : "Question 已回答";
        return List.of(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("confirmation", status, "用户问题", summary, "question", requestId,
                eventType, null, Map.of()),
            context));
    }

    private List<RuntimeEventEnvelope> translateMessagePart(String opencodeSessionId, String agentSessionId,
                                                            JsonNode properties, String eventType,
                                                            RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode part = properties.path("part");
        if (part.isMissingNode()) part = properties;

        String partId = part.path("id").asText(part.path("partID").asText(properties.path("partID").asText("")));
        String msgId = part.path("messageID").asText(part.path("message_id").asText(
                properties.path("messageID").asText(properties.path("message_id").asText(""))));
        String partType = part.path("type").asText("");

        if (!partId.isEmpty() && !partType.isEmpty()) {
            state.recordPartMetadata(opencodeSessionId, partId, partType, msgId);
        } else if (!partId.isEmpty()) {
            OpenCodeTranslationState.PartMetadata metadata = state.findPartMetadata(opencodeSessionId, partId);
            if (metadata != null) {
                if (partType.isEmpty()) partType = metadata.partType();
                if (msgId.isEmpty()) msgId = metadata.messageId();
            }
        }

        if (!msgId.isEmpty() && state.isUserMessage(opencodeSessionId, msgId)) return result;

        String delta = properties.path("delta").asText("");

        if ("text".equals(partType)) {
            String text = resolvePartText(opencodeSessionId, part, delta);
            if (!text.isEmpty()) {
                ObjectNode payload = payloadObject("assistant_delta", text,
                    projectionMeta(eventType, partType, Map.of("delta", text, "messageId", msgId, "partId", partId)));
                attachOpenCodePart(payload, properties, part, eventType, partType, msgId, partId);
                result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_DELTA, agentSessionId,
                    opencodeSessionId, payload));
            }
        } else if ("tool".equals(partType)) {
            result.addAll(translateToolPart(opencodeSessionId, agentSessionId, part, eventType, context));
        } else if ("reasoning".equals(partType)) {
            String summary = safeReasoningSummary(part, delta);
            if (!summary.isBlank()) {
                ObjectNode payload = (ObjectNode) processTracePayload("reasoning", "running", "思考", summary, null, null,
                        eventType, partType, Map.of("messageId", msgId, "partId", partId));
                attachOpenCodePart(payload, properties, part, eventType, partType, msgId, partId);
                result.add(buildProcessTraceEnvelope(
                    RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                    payload,
                    context));
            }
        } else if ("file".equals(partType) || "patch".equals(partType) || "artifact".equals(partType)) {
            result.addAll(translateArtifactPart(opencodeSessionId, agentSessionId, part, eventType, partType, context));
        } else if ("step-start".equals(partType) || "step-finish".equals(partType)
                || "snapshot".equals(partType) || "compaction".equals(partType)) {
            result.addAll(translateLifecyclePart(opencodeSessionId, agentSessionId, part, eventType, partType, context));
        } else if ("retry".equals(partType)) {
            result.addAll(translateRetryPart(opencodeSessionId, agentSessionId, part, eventType, context));
        } else if ("subtask".equals(partType)) {
            result.addAll(translateSubtaskPart(opencodeSessionId, agentSessionId, part, eventType, context));
        } else if ("agent".equals(partType) || "agent-handoff".equals(partType)) {
            result.addAll(translateAgentHandoffPart(opencodeSessionId, agentSessionId, part, eventType, context));
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateMessagePartRemoved(String opencodeSessionId, String agentSessionId,
                                                                    JsonNode properties,
                                                                    RuntimeTranslationContext context) {
        String partId = properties.path("partID").asText(properties.path("partId").asText(""));
        String messageId = properties.path("messageID").asText(properties.path("messageId").asText(""));
        ObjectNode payload = (ObjectNode) processTracePayload("part_removed", "completed", "消息片段已移除",
                partId.isBlank() ? "OpenCode 移除了一个消息片段" : "OpenCode 移除了消息片段 " + partId,
                null, null, "message.part.removed", null, Map.of());
        if (!messageId.isBlank()) payload.put("messageId", messageId);
        if (!partId.isBlank()) payload.put("partId", partId);
        payload.set("rawProperties", properties.deepCopy());
        return List.of(buildProcessTraceEnvelope(RuntimeEventTypes.PROCESS_TRACE, agentSessionId,
                opencodeSessionId, payload, context));
    }

    private String resolvePartText(String opencodeSessionId, JsonNode part, String delta) {
        String partId = part.path("id").asText(part.path("partID").asText(""));
        if (!delta.isEmpty()) {
            return state.recordTextDelta(opencodeSessionId, partId, delta);
        }
        String partText = part.path("text").asText("");
        if (partText.isEmpty()) return "";
        return state.recordTextSnapshot(opencodeSessionId, partId, partText);
    }

    private List<RuntimeEventEnvelope> translateToolPart(String opencodeSessionId, String agentSessionId,
                                                           JsonNode part, String eventType,
                                                           RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String callId = part.path("callID").asText(part.path("call_id").asText(part.path("id").asText("tool_" + System.currentTimeMillis())));
        String skillName = part.path("tool").asText(part.path("name").asText("unknown"));
        String displayName = part.path("name").asText(skillName);
        JsonNode stateNode = part.path("state");
        String status = stateNode.path("status").asText("running");

        result.addAll(translateQuestionToolPart(opencodeSessionId, agentSessionId, part, eventType,
                callId, skillName, displayName, stateNode, context));

        if (("running".equals(status) || "completed".equals(status) || "error".equals(status))
                && state.addRunningTool(opencodeSessionId, callId)) {
            ObjectNode startedPayload = payloadObject("skill_started", skillName,
                projectionMeta(eventType, "tool", Map.of(
                    "toolCallId", callId, "rawName", skillName, "displayName", displayName)));
            attachToolState(startedPayload, part);
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_STARTED, agentSessionId,
                opencodeSessionId, startedPayload));
            ObjectNode tracePayload = (ObjectNode) processTracePayload("tool_call", "running", "调用工具",
                    "正在调用 " + displayName, skillName, callId, eventType, "tool", Map.of());
            attachToolState(tracePayload, part);
            result.add(buildProcessTraceEnvelope(
                RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                tracePayload,
                context));
        }

        if ("completed".equals(status) || "error".equals(status)) {
            String output = "error".equals(status)
                    ? stringifyValue(stateNode.path("error").isMissingNode() ? part.path("error") : stateNode.path("error"))
                    : stringifyValue(stateNode.path("output").isMissingNode()
                    ? (stateNode.path("result").isMissingNode() ? part.path("output") : stateNode.path("result"))
                    : stateNode.path("output"));
            boolean isError = "error".equals(status);
            ObjectNode completedPayload = payloadObject("skill_completed", skillName,
                projectionMeta(eventType, "tool", Map.of(
                    "toolCallId", callId, "rawName", skillName, "displayName", displayName,
                    "isError", isError, "output", output)));
            attachToolState(completedPayload, part);
            result.add(buildEnvelope(RuntimeEventTypes.TOOL_COMPLETED, agentSessionId,
                opencodeSessionId, completedPayload));
            String traceStatus = isError ? "failed" : "completed";
            String traceSummary = isError ? displayName + " 调用失败" : displayName + " 调用完成";
            ObjectNode tracePayload = (ObjectNode) processTracePayload("tool_call", traceStatus, "调用工具",
                    traceSummary, skillName, callId, eventType, "tool", Map.of("output", output, "isError", isError));
            attachToolState(tracePayload, part);
            result.add(buildProcessTraceEnvelope(
                RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
                tracePayload,
                context));
            state.removeRunningTool(opencodeSessionId, callId);
        }

        return result;
    }

    private List<RuntimeEventEnvelope> translateQuestionToolPart(String opencodeSessionId, String agentSessionId,
                                                                  JsonNode part, String eventType,
                                                                  String callId, String skillName, String displayName,
                                                                  JsonNode stateNode,
                                                                  RuntimeTranslationContext context) {
        if (!isQuestionTool(skillName, displayName)) {
            return List.of();
        }

        JsonNode questionInput = findQuestionInput(part, stateNode);
        JsonNode questions = questionInput.path("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            return List.of();
        }

        String requestId = selectQuestionRequestId(
                questionInput.path("requestID").asText(""),
                questionInput.path("requestId").asText(""),
                questionInput.path("id").asText(""),
                part.path("requestID").asText(""),
                part.path("requestId").asText(""),
                callId,
                part.path("id").asText(""));
        String messageId = firstNonBlank(part.path("messageID").asText(""), part.path("message_id").asText(""));

        return buildQuestionRequestedEnvelopes(opencodeSessionId, agentSessionId, requestId, questions,
                callId, messageId, eventType, "tool", context);
    }

    private String selectQuestionRequestId(String... candidates) {
        for (String candidate : candidates) {
            if (isOpenCodeQuestionRequestId(candidate)) {
                return candidate.trim();
            }
        }
        String fallback = firstNonBlank(candidates);
        if (!fallback.isBlank()) {
            log.warn("OpenCode question request id does not match expected prefix 'que': {}", fallback);
        }
        return "";
    }

    private boolean isOpenCodeQuestionRequestId(String value) {
        return value != null && value.trim().startsWith("que");
    }

    private boolean isQuestionTool(String skillName, String displayName) {
        String raw = firstNonBlank(skillName, displayName).toLowerCase();
        return raw.equals("question")
                || raw.endsWith(".question")
                || raw.endsWith("/question")
                || raw.endsWith("-question")
                || raw.endsWith("_question")
                || raw.equals("ask_question");
    }

    private JsonNode findQuestionInput(JsonNode part, JsonNode stateNode) {
        for (JsonNode candidate : List.of(
                stateNode.path("input"),
                stateNode.path("args"),
                stateNode.path("params"),
                part.path("input"),
                part.path("args"),
                part.path("params"),
                part)) {
            if (candidate.path("questions").isArray()) {
                return candidate;
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private List<RuntimeEventEnvelope> translatePermissionClosed(String opencodeSessionId, String agentSessionId,
                                                                  JsonNode properties,
                                                                  RuntimeTranslationContext context) {
        String requestId = properties.path("requestID").asText(properties.path("requestId").asText(""));
        String reply = properties.path("reply").asText("");
        String confirmationId = PermissionConfirmationHandler.confirmationIdFor(opencodeSessionId, requestId);
        ObjectNode payload = (ObjectNode) processTracePayload("confirmation", "completed", "权限确认",
                reply.isBlank() ? "权限请求已处理" : "权限请求已处理：" + reply,
                "permission", requestId, "permission.replied", null,
                Map.of("confirmationId", confirmationId));
        if (!reply.isBlank()) payload.put("reply", reply);
        payload.set("rawProperties", properties.deepCopy());
        return List.of(buildProcessTraceEnvelope(RuntimeEventTypes.PROCESS_TRACE, agentSessionId,
                opencodeSessionId, payload, context));
    }

    private List<RuntimeEventEnvelope> translateSessionStatus(String opencodeSessionId, String agentSessionId,
                                                                JsonNode properties, RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        JsonNode statusNode = properties.path("status");
        String rawStatus = statusNode.isObject() ? statusNode.path("type").asText("") : statusNode.asText("");
        if (rawStatus.isEmpty()) rawStatus = properties.path("type").asText("unknown");
        String status = "busy".equals(rawStatus) ? "running" : rawStatus;

        result.add(buildEnvelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, agentSessionId,
            opencodeSessionId, payloadNode("status", status,
            projectionMeta("session.status", null, Map.of()))));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("node_status", traceStatusForRuntimeStatus(status), "状态",
                statusSummary(status), null, null, "session.status", null, Map.of()),
            context));

        if ("idle".equals(status) || "waiting_user".equals(status)) {
            result.add(buildEnvelope(RuntimeEventTypes.CONVERSATION_COMPLETED, agentSessionId,
                opencodeSessionId, payloadNode("_completed", true,
                projectionMeta("session.status", null, Map.of()))));
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
        String title = properties.path("title").asText(defaultPermissionTitle(permission));
        String confirmationId = PermissionConfirmationHandler.confirmationIdFor(opencodeSessionId, permissionId);
        String targetPath = extractPermissionTargetPath(properties);
        String parentDir = firstNonBlank(
                textAt(properties.path("metadata"), "parentDir"),
                textAt(properties.path("metadata"), "parent_dir"));
        Map<String, Object> permissionMeta = new LinkedHashMap<>();
        permissionMeta.put("permissionId", permissionId);
        permissionMeta.put("confirmationId", confirmationId);
        permissionMeta.put("title", title);
        permissionMeta.put("permission", permission);
        if (!targetPath.isBlank()) permissionMeta.put("filePath", targetPath);
        if (!parentDir.isBlank()) permissionMeta.put("parentDir", parentDir);
        String patterns = joinTextArray(properties.path("patterns"));
        String always = joinTextArray(properties.path("always"));
        if (!patterns.isBlank()) permissionMeta.put("patterns", patterns);
        if (!always.isBlank()) permissionMeta.put("always", always);

        result.add(buildContextEnvelope(RuntimeEventTypes.PERMISSION_REQUESTED, agentSessionId,
            opencodeSessionId, payloadNode("permission_required", skillName,
            projectionMeta("permission.asked", null, permissionMeta)), context));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("confirmation", "waiting", "权限确认", title, skillName, permissionId,
                "permission.asked", null, Map.of()),
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
            opencodeSessionId, payloadNode("status", "failed",
            projectionMeta("session.error", null, Map.of("reason", reason)))));
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            processTracePayload("error", "failed", "异常", reason, null, null,
                "session.error", null, Map.of()),
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateArtifactPart(String opencodeSessionId, String agentSessionId,
                                                              JsonNode part, String eventType, String partType,
                                                              RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String artifactId = part.path("id").asText("");
        String filePath = part.path("path").asText(part.path("name").asText(""));
        String summary = switch (partType) {
            case "file" -> "生成文件 " + filePath;
            case "patch" -> "应用补丁 " + filePath;
            default -> "产物 " + filePath;
        };

        ObjectNode payload = (ObjectNode) processTracePayload("artifact", "completed", "产物变更", summary, null, null,
                eventType, partType, Map.of("artifactId", artifactId, "filePath", filePath));
        attachOpenCodePart(payload, part, part, eventType, partType,
                part.path("messageID").asText(part.path("message_id").asText("")), artifactId);
        copyIfPresent(payload, part, "url");
        copyIfPresent(payload, part, "mime");
        copyIfPresent(payload, part, "filename");
        copyIfPresent(payload, part, "hash");
        if (part.has("files")) payload.set("files", part.get("files").deepCopy());
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            payload,
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateLifecyclePart(String opencodeSessionId, String agentSessionId,
                                                               JsonNode part, String eventType, String partType,
                                                               RuntimeTranslationContext context) {
        String partId = part.path("id").asText(part.path("partID").asText(""));
        String messageId = part.path("messageID").asText(part.path("message_id").asText(""));
        String kind = partType.replace('-', '_');
        String title = switch (partType) {
            case "step-start" -> "步骤开始";
            case "step-finish" -> "步骤完成";
            case "snapshot" -> "会话快照";
            case "compaction" -> "上下文压缩";
            default -> "OpenCode 事件";
        };
        String summary = switch (partType) {
            case "step-finish" -> firstNonBlank(part.path("reason").asText(""), "OpenCode 完成一个执行步骤");
            case "compaction" -> part.path("auto").asBoolean(false) ? "OpenCode 自动压缩了上下文" : "OpenCode 压缩了上下文";
            default -> title;
        };
        ObjectNode payload = (ObjectNode) processTracePayload(kind, "completed", title, summary, null, null,
                eventType, partType, Map.of("messageId", messageId, "partId", partId));
        attachOpenCodePart(payload, part, part, eventType, partType, messageId, partId);
        if (part.has("cost")) payload.set("cost", part.get("cost").deepCopy());
        if (part.has("tokens")) payload.set("tokens", part.get("tokens").deepCopy());
        if (part.has("snapshot")) payload.set("snapshot", part.get("snapshot").deepCopy());
        return List.of(buildProcessTraceEnvelope(RuntimeEventTypes.PROCESS_TRACE, agentSessionId,
                opencodeSessionId, payload, context));
    }

    private List<RuntimeEventEnvelope> translateRetryPart(String opencodeSessionId, String agentSessionId,
                                                           JsonNode part, String eventType,
                                                           RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String retryId = part.path("id").asText("");
        int attempt = part.path("attempt").asInt(1);
        int maxAttempts = part.path("maxAttempts").asInt(3);
        String reason = part.path("reason").asText("");
        String summary = "重试中 (" + attempt + "/" + maxAttempts + ")" + (reason.isEmpty() ? "" : ": " + reason);

        ObjectNode payload = (ObjectNode) processTracePayload("retry", "running", "重试", summary, null, null,
                eventType, "retry", Map.of("operationId", retryId));
        attachOpenCodePart(payload, part, part, eventType, "retry",
                part.path("messageID").asText(part.path("message_id").asText("")), retryId);
        if (part.has("error")) payload.set("error", part.get("error").deepCopy());
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            payload,
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateSubtaskPart(String opencodeSessionId, String agentSessionId,
                                                             JsonNode part, String eventType,
                                                             RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String subtaskId = part.path("id").asText("");
        String name = part.path("name").asText("子任务");
        String subtaskStatus = part.path("status").asText("running");

        ObjectNode payload = (ObjectNode) processTracePayload("subtask", subtaskStatus, "子任务", name, null, null,
                eventType, "subtask", Map.of("operationId", subtaskId));
        attachOpenCodePart(payload, part, part, eventType, "subtask",
                part.path("messageID").asText(part.path("message_id").asText("")), subtaskId);
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            payload,
            context));
        return result;
    }

    private List<RuntimeEventEnvelope> translateAgentHandoffPart(String opencodeSessionId, String agentSessionId,
                                                                   JsonNode part, String eventType,
                                                                   RuntimeTranslationContext context) {
        List<RuntimeEventEnvelope> result = new ArrayList<>();
        String handoffId = part.path("id").asText("");
        String targetAgent = firstNonBlank(part.path("targetAgent").asText(""), part.path("name").asText(""), "unknown");
        String parentStepId = part.path("parentStepId").asText("");
        String summary = "移交至 " + targetAgent;

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("operationId", handoffId);
        if (!parentStepId.isEmpty()) extra.put("parentStepId", parentStepId);

        ObjectNode payload = (ObjectNode) processTracePayload("agent", "running", "Agent", summary, null, null,
                eventType, part.path("type").asText("agent"), extra);
        attachOpenCodePart(payload, part, part, eventType, part.path("type").asText("agent"),
                part.path("messageID").asText(part.path("message_id").asText("")), handoffId);
        if (part.has("source")) payload.set("source", part.get("source").deepCopy());
        result.add(buildProcessTraceEnvelope(
            RuntimeEventTypes.PROCESS_TRACE, agentSessionId, opencodeSessionId,
            payload,
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
        return payloadObject(type, label, extra);
    }

    private ObjectNode payloadObject(String type, Object label, Map<String, Object> extra) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("type", type);
        if (label instanceof String s) node.put("label", s);
        else if (label instanceof Boolean b) node.put("label", b);
        else if (label instanceof Number n) node.put("label", n.doubleValue());
        for (var entry : extra.entrySet()) {
            putValue(node, entry.getKey(), entry.getValue());
        }
        return node;
    }

    private Map<String, Object> projectionMeta(String rawEventType, String rawPartType, Map<String, Object> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (rawEventType != null) meta.put("rawEventType", rawEventType);
        if (rawPartType != null) meta.put("rawPartType", rawPartType);
        meta.putAll(extra);
        return meta;
    }

    private void putValue(ObjectNode node, String key, Object v) {
        if (v instanceof String s) node.put(key, s);
        else if (v instanceof Boolean b) node.put(key, b);
        else if (v instanceof Number n) node.put(key, n.doubleValue());
    }

    private void attachOpenCodePart(ObjectNode node, JsonNode properties, JsonNode part,
                                    String rawEventType, String rawPartType,
                                    String messageId, String partId) {
        if (rawEventType != null && !rawEventType.isBlank() && !node.has("rawEventType")) node.put("rawEventType", rawEventType);
        if (rawPartType != null && !rawPartType.isBlank() && !node.has("rawPartType")) node.put("rawPartType", rawPartType);
        if (messageId != null && !messageId.isBlank() && !node.has("messageId")) node.put("messageId", messageId);
        if (partId != null && !partId.isBlank() && !node.has("partId")) node.put("partId", partId);
        String field = properties.path("field").asText("");
        if (!field.isBlank()) node.put("field", field);
        if (properties.has("delta") && !node.has("delta")) node.put("delta", properties.path("delta").asText(""));
        if (part != null && part.isObject()) node.set("rawPart", part.deepCopy());
    }

    private void attachToolState(ObjectNode node, JsonNode part) {
        attachOpenCodePart(node, part, part, "message.part.updated", "tool",
                part.path("messageID").asText(part.path("message_id").asText("")),
                part.path("id").asText(part.path("partID").asText("")));
        JsonNode stateNode = part.path("state");
        if (stateNode.has("input")) node.set("input", stateNode.get("input").deepCopy());
        if (stateNode.has("output")) node.set("output", stateNode.get("output").deepCopy());
        if (stateNode.has("result") && !node.has("output")) node.set("output", stateNode.get("result").deepCopy());
        if (stateNode.has("error")) node.set("error", stateNode.get("error").deepCopy());
        if (stateNode.has("metadata")) node.set("metadata", stateNode.get("metadata").deepCopy());
        if (stateNode.has("time")) node.set("time", stateNode.get("time").deepCopy());
        if (stateNode.has("attachments")) node.set("attachments", stateNode.get("attachments").deepCopy());
        copyIfPresent(node, stateNode, "title");
        copyIfPresent(node, part, "metadata");
    }

    private void copyIfPresent(ObjectNode target, JsonNode source, String field) {
        if (source != null && source.has(field) && !source.get(field).isMissingNode()) {
            target.set(field, source.get(field).deepCopy());
        }
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

    private RuntimeEventEnvelope buildContextEnvelope(String type, String agentSessionId,
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
        if (!summary.isBlank()) return truncate(summary, 1000);

        String text = firstNonBlank(part.path("text").asText(""), delta);
        return text.isBlank() ? "" : truncate(text, 2000);
    }

    private String extractPermissionTargetPath(JsonNode properties) {
        String direct = firstNonBlank(
                textAt(properties, "filePath"),
                textAt(properties, "filepath"),
                textAt(properties, "file_path"),
                textAt(properties.path("metadata"), "filepath"),
                textAt(properties.path("metadata"), "filePath"),
                textAt(properties.path("metadata"), "parentDir"),
                textAt(properties.path("metadata"), "parent_dir"),
                textAt(properties, "path"),
                textAt(properties, "target"),
                textAt(properties, "file"),
                textAt(properties.path("tool"), "filePath"),
                textAt(properties.path("tool"), "path"),
                textAt(properties.path("tool"), "target")
        );
        if (!direct.isBlank()) return direct;
        return firstPathLikeText(properties);
    }

    private String defaultPermissionTitle(String permission) {
        if ("external_directory".equals(permission)) return "OpenCode 请求访问外部目录";
        return "OpenCode permission: " + permission;
    }

    private String joinTextArray(JsonNode node) {
        if (node == null || !node.isArray()) return "";
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            if (child.isTextual() && !child.asText("").isBlank()) {
                values.add(child.asText());
            }
        }
        return String.join(", ", values);
    }

    private String textAt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText("") : "";
    }

    private String firstPathLikeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) {
            String text = node.asText("");
            String matched = matchPath(text);
            return matched == null ? "" : matched;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = firstPathLikeText(child);
                if (!found.isBlank()) return found;
            }
            return "";
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey().toLowerCase();
                if (key.contains("path") || key.contains("file") || key.contains("target")
                        || key.contains("arg") || key.contains("input") || key.contains("command")) {
                    String found = firstPathLikeText(entry.getValue());
                    if (!found.isBlank()) return found;
                }
            }
        }
        return "";
    }

    private String matchPath(String text) {
        var windows = java.util.regex.Pattern
                .compile("[A-Za-z]:[\\\\/][^\\s\"'`<>|]+(?:[\\\\/][^\\s\"'`<>|]+)*")
                .matcher(text);
        if (windows.find()) return windows.group();

        var posix = java.util.regex.Pattern
                .compile("(?:~|\\.{1,2}|/)[/\\w@.+-]+(?:/[\\w@.+-]+)*(?:\\.[A-Za-z0-9]+)?")
                .matcher(text);
        return posix.find() ? posix.group() : null;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private JsonNode processTracePayload(String kind, String status, String title, String summary,
                                          String toolName, String toolCallId,
                                          String rawEventType, String rawPartType,
                                          Map<String, Object> extraMeta) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("kind", kind);
        node.put("status", status);
        node.put("title", title);
        node.put("summary", summary);
        if (toolName != null) node.put("toolName", toolName);
        if (toolCallId != null) node.put("toolCallId", toolCallId);
        node.put("visibility", "public_summary");
        if (rawEventType != null) node.put("rawEventType", rawEventType);
        if (rawPartType != null) node.put("rawPartType", rawPartType);
        for (var entry : extraMeta.entrySet()) {
            putValue(node, entry.getKey(), entry.getValue());
        }
        return node;
    }
}
