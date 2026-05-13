package com.agentcenter.bridge.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class WorkflowContextAnchorService {

    private static final int RECENT_RUNTIME_EVENTS_LIMIT = 160;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RECOVERY_REASON =
            "检测到 OpenCode 已发生上下文压缩。请以 AgentCenter 本轮输入上下文中的工作项、当前节点、上游产物、待处理交互和用户回答为准，恢复当前节点并继续。";
    private static final String RECOVERY_SUMMARY =
            "检测到 OpenCode 上下文压缩，已重新注入当前节点、上游产物摘要和待处理交互。";

    private final RuntimeEventMapper eventMapper;
    private final RuntimeEventService runtimeEventService;

    public WorkflowContextAnchorService(RuntimeEventMapper eventMapper,
                                        RuntimeEventService runtimeEventService) {
        this.eventMapper = eventMapper;
        this.runtimeEventService = runtimeEventService;
    }

    public ContextAnchorDecision decide(String sessionId, String nodeInstanceId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ContextAnchorDecision.none();
        }

        long latestCompactionSeq = -1;
        long latestAnchorSeq = -1;

        List<RuntimeEventEntity> events = eventMapper.findRecentBySessionId(
                sessionId, RECENT_RUNTIME_EVENTS_LIMIT);
        for (RuntimeEventEntity event : events) {
            if (!RuntimeEventType.PROCESS_TRACE.name().equals(event.getEventType())) {
                continue;
            }
            if (!matchesWorkflowNode(event, nodeInstanceId)) {
                continue;
            }

            JsonNode payload = parsePayload(event.getPayloadJson());
            if (payload == null || payload.isMissingNode()) {
                continue;
            }

            long seq = event.getSeqNo() != null ? event.getSeqNo() : 0L;
            if (isCompactionTrace(payload)) {
                latestCompactionSeq = Math.max(latestCompactionSeq, seq);
            }
            if (isAnchorTrace(payload)) {
                latestAnchorSeq = Math.max(latestAnchorSeq, seq);
            }
        }

        if (latestCompactionSeq > latestAnchorSeq) {
            return new ContextAnchorDecision(true, latestCompactionSeq, latestAnchorSeq);
        }
        return ContextAnchorDecision.none();
    }

    public boolean recoveryRequired(String sessionId, String nodeInstanceId) {
        return decide(sessionId, nodeInstanceId).required();
    }

    public String inputSection(ContextAnchorDecision decision) {
        if (decision == null || !decision.required()) {
            return "";
        }
        return """
                ## AGENTCENTER_CONTEXT_ANCHOR
                - 状态：RECOVERED_AFTER_OPENCODE_COMPACTION
                - 原因：%s
                - 恢复策略：本轮已重新注入工作项、当前节点、上游产物、待处理交互和用户回答；不要依赖压缩前对话记忆判断流程位置。
                - 最近压缩事件序号：%s

                """.formatted(RECOVERY_REASON, decision.latestCompactionSeq());
    }

    public void publishContextAnchor(ContextAnchorDecision decision,
                                     String sessionId,
                                     String workItemId,
                                     String workflowInstanceId,
                                     String nodeInstanceId) {
        if (decision == null || !decision.required()) {
            return;
        }
        runtimeEventService.publishEvent(new RuntimeEventDto(
                null,
                sessionId,
                workItemId,
                workflowInstanceId,
                nodeInstanceId,
                RuntimeEventType.PROCESS_TRACE,
                RuntimeEventSource.WORKFLOW,
                buildContextAnchorPayload(decision),
                null
        ));
    }

    private String buildContextAnchorPayload(ContextAnchorDecision decision) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("kind", "context_anchor");
        payload.put("status", "completed");
        payload.put("title", "已恢复工作流上下文");
        payload.put("summary", RECOVERY_SUMMARY);
        payload.put("trigger", "opencode_compaction");
        payload.put("visibility", "public_summary");
        payload.put("injectedContext", "workflow_node_input");
        payload.put("latestCompactionSeq", decision.latestCompactionSeq());
        payload.put("latestAnchorSeq", decision.latestAnchorSeq());
        return payload.toString();
    }

    private boolean matchesWorkflowNode(RuntimeEventEntity event, String nodeInstanceId) {
        String eventNodeId = event.getWorkflowNodeInstanceId();
        return nodeInstanceId == null || nodeInstanceId.isBlank()
                || eventNodeId == null || eventNodeId.isBlank()
                || nodeInstanceId.equals(eventNodeId);
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(payloadJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCompactionTrace(JsonNode payload) {
        return "compaction".equals(payload.path("kind").asText(""))
                || "compaction".equals(payload.path("rawPartType").asText(""));
    }

    private boolean isAnchorTrace(JsonNode payload) {
        String kind = payload.path("kind").asText("");
        return "prompt_debug".equals(kind) || "context_anchor".equals(kind);
    }

    public record ContextAnchorDecision(
            boolean required,
            long latestCompactionSeq,
            long latestAnchorSeq
    ) {
        private static ContextAnchorDecision none() {
            return new ContextAnchorDecision(false, -1, -1);
        }
    }
}
