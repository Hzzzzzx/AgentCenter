package com.agentcenter.bridge.application.confirmation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ConfirmationCreatedEventPayloadBuilder {

    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConfirmationCreatedEventPayloadBuilder(WorkItemMapper workItemMapper,
                                                   WorkflowMapper workflowMapper) {
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
    }

    public String buildPayload(ConfirmationRequestEntity entity) {
        return buildPayload(entity, Map.of());
    }

    public String buildPayload(ConfirmationRequestEntity entity, Map<String, Object> extraFields) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", entity.getId());
            payload.put("confirmationId", entity.getId());
            payload.put("requestType", entity.getRequestType());
            payload.put("status", entity.getStatus());
            putIfPresent(payload, "workItemId", entity.getWorkItemId());
            putIfPresent(payload, "workflowInstanceId", entity.getWorkflowInstanceId());
            putIfPresent(payload, "workflowNodeInstanceId", entity.getWorkflowNodeInstanceId());
            putIfPresent(payload, "agentSessionId", entity.getAgentSessionId());
            putIfPresent(payload, "skillName", entity.getSkillName());
            putIfPresent(payload, "title", entity.getTitle());
            putIfPresent(payload, "content", entity.getContent());
            putIfPresent(payload, "contextSummary", entity.getContextSummary());
            if (entity.getOptionsJson() != null && !entity.getOptionsJson().isBlank()) {
                payload.put("optionsJson", entity.getOptionsJson());
            }
            putIfPresent(payload, "priority", entity.getPriority());
            payload.put("createdAt", entity.getCreatedAt());
            putIfPresent(payload, "interactionId", entity.getInteractionId());
            putIfPresent(payload, "interactionType", entity.getInteractionType());
            putIfPresent(payload, "interactionSchemaJson", entity.getInteractionSchemaJson());
            putIfPresent(payload, "interactionContextJson", entity.getInteractionContextJson());
            payload.put("interactionRequired", entity.getInteractionRequired() != null ? entity.getInteractionRequired() != 0 : null);
            if (entity.getInteractionOrderNo() != null) payload.put("interactionOrderNo", entity.getInteractionOrderNo());
            appendWorkItemDisplayFields(payload, entity.getWorkItemId());
            String nodeName = resolveWorkflowNodeName(entity.getWorkflowNodeInstanceId());
            if (nodeName != null) payload.put("workflowNodeName", nodeName);
            if (extraFields != null && !extraFields.isEmpty()) {
                payload.putAll(extraFields);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"confirmationId\":\"" + entity.getId() + "\"}";
        }
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private void appendWorkItemDisplayFields(Map<String, Object> payload, String workItemId) {
        if (workItemId == null) return;
        WorkItemEntity workItem = workItemMapper.findById(workItemId);
        if (workItem == null) return;
        payload.put("workItemCode", workItem.getCode());
        payload.put("workItemType", workItem.getType());
        payload.put("workItemTitle", workItem.getTitle());
    }

    private String resolveWorkflowNodeName(String nodeInstanceId) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) return null;
        var node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) return null;
        var instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        if (instance == null) return null;
        return workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId()).stream()
                .filter(def -> def.getId().equals(node.getNodeDefinitionId()))
                .findFirst()
                .map(def -> def.getName())
                .orElse(null);
    }
}
