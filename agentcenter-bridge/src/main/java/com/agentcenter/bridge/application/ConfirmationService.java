package com.agentcenter.bridge.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

@Service
public class ConfirmationService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfirmationMapper confirmationMapper;
    private final WorkflowCommandService workflowCommandService;
    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final RuntimeEventService runtimeEventService;
    private final PermissionConfirmationHandler permissionConfirmationHandler;

    public ConfirmationService(ConfirmationMapper confirmationMapper,
                               WorkflowCommandService workflowCommandService,
                               WorkItemMapper workItemMapper,
                               WorkflowMapper workflowMapper,
                               AgentMessageMapper agentMessageMapper,
                               RuntimeEventService runtimeEventService,
                               PermissionConfirmationHandler permissionConfirmationHandler) {
        this.confirmationMapper = confirmationMapper;
        this.workflowCommandService = workflowCommandService;
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.runtimeEventService = runtimeEventService;
        this.permissionConfirmationHandler = permissionConfirmationHandler;
    }

    public List<ConfirmationRequestDto> listPending() {
        return confirmationMapper.findByStatus("PENDING").stream()
                .map(this::toDto).toList();
    }

    public List<ConfirmationRequestDto> listByStatus(String status) {
        return confirmationMapper.findByStatus(status).stream()
                .map(this::toDto).toList();
    }

    public ConfirmationRequestDto getById(String id) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        return toDto(entity);
    }

    @Transactional
    public ConfirmationRequestDto enterSession(String id) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        String status = entity.getStatus();
        if (ConfirmationStatus.IN_CONVERSATION.name().equals(status)) {
            // Idempotent: already in conversation, just return
            return toDto(entity);
        }
        if (!ConfirmationStatus.PENDING.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirmation is not PENDING, current status: " + status);
        }
        entity.setStatus(ConfirmationStatus.IN_CONVERSATION.name());
        entity.setUpdatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        confirmationMapper.update(entity);
        return toDto(entity);
    }

    @Transactional
    public ConfirmationRequestDto resolve(String id, ResolveConfirmationRequest request) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        String currentStatus = entity.getStatus();
        if (!ConfirmationStatus.PENDING.name().equals(currentStatus)
                && !ConfirmationStatus.IN_CONVERSATION.name().equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot resolve confirmation in status: " + currentStatus);
        }

        ConfirmationActionType actionType = request.actionType();
        ConfirmationRequestType requestType = ConfirmationRequestType.valueOf(entity.getRequestType());

        boolean validAction = switch (requestType) {
            case EXCEPTION -> ConfirmationActionType.RETRY.equals(actionType)
                    || ConfirmationActionType.SKIP.equals(actionType)
                    || ConfirmationActionType.REJECT.equals(actionType);
            case APPROVAL, CONFIRM, PERMISSION -> ConfirmationActionType.APPROVE.equals(actionType)
                    || ConfirmationActionType.REJECT.equals(actionType);
            case DECISION -> {
                boolean isWorkflowAdvance = "WORKFLOW_ADVANCE".equals(entity.getInteractionType());
                if (isWorkflowAdvance) {
                    yield ConfirmationActionType.CHOOSE.equals(actionType)
                            || ConfirmationActionType.ADVANCE.equals(actionType)
                            || ConfirmationActionType.SUPPLEMENT.equals(actionType)
                            || ConfirmationActionType.RETRY.equals(actionType)
                            || ConfirmationActionType.REJECT.equals(actionType);
                } else {
                    yield ConfirmationActionType.CHOOSE.equals(actionType)
                            || ConfirmationActionType.REJECT.equals(actionType);
                }
            }
            case INPUT_REQUIRED -> ConfirmationActionType.SUPPLEMENT.equals(actionType)
                    || ConfirmationActionType.REJECT.equals(actionType);
        };
        if (!validAction) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    requestType + " confirmation does not accept " + actionType + ". "
                    + "Valid actions for " + requestType + ": " + validActionsFor(requestType));
        }

        if (ConfirmationRequestType.PERMISSION.equals(requestType)) {
            respondPermissionBeforeResolving(entity, actionType);
        }

        if (ConfirmationActionType.REJECT.equals(actionType)) {
            return handleReject(entity, request);
        }

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.RESOLVED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(request.comment());
        entity.setUpdatedAt(now);
        if (request.payload() != null) {
            try {
                entity.setResolutionPayloadJson(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(request.payload()));
            } catch (Exception ignored) {
            }
        }
        confirmationMapper.update(entity);

        String actionDescription = buildActionDescription(actionType, request);
        writeResolutionLedgerMessage(entity, actionDescription);

        var nodeInstanceId = entity.getWorkflowNodeInstanceId();
        boolean shouldDispatchWorkflow = nodeInstanceId != null && !hasOtherBlockingForNode(entity);
        boolean isDecision = ConfirmationRequestType.DECISION.name().equals(entity.getRequestType());
        boolean isException = ConfirmationRequestType.EXCEPTION.name().equals(entity.getRequestType());
        boolean isSkip = ConfirmationActionType.SKIP.equals(actionType);
        // Only route DECISION as system action for WORKFLOW_ADVANCE confirmations
        boolean isWorkflowAdvance = isDecision
                && "WORKFLOW_ADVANCE".equals(entity.getInteractionType());

        publishResolutionEventAfterCommit(entity, actionType, actionDescription, () -> {
            if (shouldDispatchWorkflow) {
                if (isWorkflowAdvance) {
                    switch (actionType) {
                        case ADVANCE -> workflowCommandService.completeNodeAndScheduleAdvance(nodeInstanceId);
                        case SUPPLEMENT -> workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                        case RETRY -> workflowCommandService.retryNode(nodeInstanceId);
                        default -> workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                    }
                } else if (isDecision) {
                    // Non-WORKFLOW_ADVANCE DECISION: treat as normal confirmation
                    workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                } else if (isException) {
                    if (isSkip) {
                        workflowCommandService.skipNode(nodeInstanceId);
                    } else {
                        workflowCommandService.retryNode(nodeInstanceId);
                    }
                } else {
                    workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                }
            }
        });

        return toDto(entity);
    }

    private void respondPermissionBeforeResolving(ConfirmationRequestEntity entity,
                                                  ConfirmationActionType actionType) {
        try {
            permissionConfirmationHandler.respondPermission(
                    entity.getRuntimeSessionId(),
                    entity.getInteractionId(),
                    ConfirmationActionType.APPROVE.equals(actionType));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to respond to OpenCode permission: " + e.getMessage(), e);
        }
    }

    private ConfirmationRequestDto handleReject(ConfirmationRequestEntity entity,
                                                 ResolveConfirmationRequest request) {
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.REJECTED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(request.comment());
        entity.setUpdatedAt(now);
        confirmationMapper.update(entity);

        writeResolutionLedgerMessage(entity,
                "用户拒绝" + (request.comment() != null ? "：" + request.comment() : "")
                        + "，工作流保持阻塞");

        publishResolutionEventAfterCommit(entity, ConfirmationActionType.REJECT,
                buildActionDescription(ConfirmationActionType.REJECT, request), null);

        return toDto(entity);
    }

    private String validActionsFor(ConfirmationRequestType requestType) {
        return switch (requestType) {
            case EXCEPTION -> "RETRY, SKIP, REJECT";
            case APPROVAL, CONFIRM, PERMISSION -> "APPROVE, REJECT";
            case DECISION -> "CHOOSE, REJECT (ADVANCE/SUPPLEMENT/RETRY only for WORKFLOW_ADVANCE)";
            case INPUT_REQUIRED -> "SUPPLEMENT, REJECT";
        };
    }

    private String buildActionDescription(ConfirmationActionType actionType,
                                           ResolveConfirmationRequest request) {
        return switch (actionType) {
            case APPROVE -> "用户确认通过" + (request.comment() != null ? "：" + request.comment() : "");
            case CHOOSE -> {
                String choiceId = extractPayloadField(request, "choiceId");
                String choiceLabel = extractPayloadField(request, "choiceLabel");
                String choice = extractPayloadField(request, "choice");
                if (choiceLabel != null) {
                    yield "用户选择：" + choiceLabel + (choiceId != null ? "（" + choiceId + "）" : "");
                } else {
                    yield "用户选择" + (choice != null ? "：" + choice : "");
                }
            }
            case SUPPLEMENT -> {
                String input = extractPayloadField(request, "input");
                if (input != null) {
                    yield "用户补充：" + input;
                }
                Object fields = request.payload() != null ? request.payload().get("fields") : null;
                if (fields instanceof Map) {
                    StringBuilder sb = new StringBuilder("用户补充：");
                    ((Map<?, ?>) fields).forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
                    yield sb.toString();
                }
                yield "用户补充" + (request.comment() != null ? "：" + request.comment() : "");
            }
            case RETRY -> "用户重试" + (request.comment() != null ? "：" + request.comment() : "");
            case SKIP -> "用户跳过" + (request.comment() != null ? "：" + request.comment() : "");
            case REJECT -> "用户拒绝" + (request.comment() != null ? "：" + request.comment() : "");
            default -> "用户操作：" + actionType.name();
        };
    }

    @SuppressWarnings("unchecked")
    private String extractPayloadField(ResolveConfirmationRequest request, String field) {
        if (request.payload() == null) return null;
        Object value = request.payload().get(field);
        return value != null ? value.toString() : null;
    }

    private void writeResolutionLedgerMessage(ConfirmationRequestEntity confirmation, String actionDescription) {
        String sessionId = confirmation.getAgentSessionId();
        if (sessionId == null || sessionId.isBlank()) return;

        int nextSeqNo = agentMessageMapper.findBySessionId(sessionId).stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;

        AgentMessageEntity message = new AgentMessageEntity();
        message.setId(java.util.UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(MessageRole.USER.name());
        message.setContent(buildResolutionUserInputMessage(confirmation, actionDescription));
        message.setContentFormat(ContentFormat.TEXT.name());
        message.setStatus(MessageStatus.COMPLETED.name());
        message.setSeqNo(nextSeqNo);
        message.setCreatedBy("confirmation-service");
        message.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        agentMessageMapper.insert(message);
    }

    private String buildResolutionUserInputMessage(ConfirmationRequestEntity confirmation,
                                                   String actionDescription) {
        String title = confirmation.getTitle() != null && !confirmation.getTitle().isBlank()
                ? confirmation.getTitle()
                : "未命名确认项";
        String requestType = confirmation.getRequestType() != null
                ? confirmation.getRequestType()
                : "UNKNOWN";
        return """
                用户输入：%s
                确认项：%s
                类型：%s
                节点：%s
                """.formatted(
                actionDescription,
                title,
                requestType,
                confirmation.getWorkflowNodeInstanceId() != null
                        ? confirmation.getWorkflowNodeInstanceId()
                        : "未关联节点"
        ).trim();
    }

    private void publishResolutionEventAfterCommit(ConfirmationRequestEntity entity,
                                                    ConfirmationActionType actionType,
                                                    String actionDescription,
                                                    Runnable afterCommitAction) {
        String sessionId = entity.getAgentSessionId();
        if (sessionId == null) {
            if (afterCommitAction != null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        afterCommitAction.run();
                    }
                });
            }
            return;
        }

        String capturedId = entity.getId();
        String capturedRequestType = entity.getRequestType();
        String capturedWorkItemId = entity.getWorkItemId();
        String capturedWorkflowInstanceId = entity.getWorkflowInstanceId();
        String capturedNodeInstanceId = entity.getWorkflowNodeInstanceId();
        String capturedActionName = actionType.name();
        String capturedTitle = entity.getTitle();
        String capturedContent = entity.getContent();
        String capturedContextSummary = entity.getContextSummary();
        String capturedOptionsJson = entity.getOptionsJson();
        String capturedComment = entity.getResolutionComment();
        String capturedResolutionPayloadJson = entity.getResolutionPayloadJson();

        String payloadJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("confirmationId", capturedId);
            payload.put("actionType", capturedActionName);
            payload.put("requestType", capturedRequestType);
            payload.put("title", capturedTitle);
            if (capturedContent != null && !capturedContent.isBlank()) {
                payload.put("question", capturedContent);
            }
            if (capturedContextSummary != null && !capturedContextSummary.isBlank()) {
                payload.put("contextSummary", capturedContextSummary);
            }
            if (capturedOptionsJson != null && !capturedOptionsJson.isBlank()) {
                payload.put("options", capturedOptionsJson);
            }
            payload.put("actionDescription", actionDescription);
            if (capturedComment != null && !capturedComment.isBlank()) {
                payload.put("comment", capturedComment);
            }
            if (capturedResolutionPayloadJson != null && !capturedResolutionPayloadJson.isBlank()) {
                payload.put("resolutionPayload", capturedResolutionPayloadJson);
            }
            payloadJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{\"confirmationId\":\"" + capturedId + "\"}";
        }

        String finalPayloadJson = payloadJson;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (afterCommitAction != null) {
                    try {
                        afterCommitAction.run();
                    } catch (Exception e) {
                        System.getLogger(ConfirmationService.class.getName())
                                .log(System.Logger.Level.WARNING,
                                        "afterCommit workflow dispatch failed for confirmation " + capturedId, e);
                    }
                }
                // dispatch must run first — CONFIRMATION_RESOLVED signals "state already updated"
                runtimeEventService.publishEvent(new RuntimeEventDto(
                        null,
                        sessionId,
                        capturedWorkItemId,
                        capturedWorkflowInstanceId,
                        capturedNodeInstanceId,
                        RuntimeEventType.CONFIRMATION_RESOLVED,
                        RuntimeEventSource.BRIDGE,
                        finalPayloadJson,
                        null
                ));
            }
        });
    }

    private void publishResolutionEvent(ConfirmationRequestEntity entity,
                                         ConfirmationActionType actionType) {
        String sessionId = entity.getAgentSessionId();
        if (sessionId == null) return;

        String payloadJson;
        try {
            payloadJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of(
                            "confirmationId", entity.getId(),
                            "actionType", actionType.name(),
                            "requestType", entity.getRequestType()
                    ));
        } catch (Exception e) {
            payloadJson = "{\"confirmationId\":\"" + entity.getId() + "\"}";
        }

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null,
                sessionId,
                entity.getWorkItemId(),
                entity.getWorkflowInstanceId(),
                entity.getWorkflowNodeInstanceId(),
                RuntimeEventType.CONFIRMATION_CREATED,
                RuntimeEventSource.BRIDGE,
                payloadJson,
                null
        ));
    }

    @Transactional
    public ConfirmationRequestDto reject(String id, String comment) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.REJECTED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(comment);
        entity.setUpdatedAt(now);
        confirmationMapper.update(entity);
        return toDto(entity);
    }

    private boolean hasOtherBlockingForNode(ConfirmationRequestEntity current) {
        if (current.getWorkItemId() == null || current.getWorkflowNodeInstanceId() == null) {
            return false;
        }
        return confirmationMapper.findByWorkItemId(current.getWorkItemId()).stream()
                .anyMatch(candidate ->
                        !candidate.getId().equals(current.getId())
                                && current.getWorkflowNodeInstanceId().equals(candidate.getWorkflowNodeInstanceId())
                                && (ConfirmationStatus.PENDING.name().equals(candidate.getStatus())
                                || ConfirmationStatus.IN_CONVERSATION.name().equals(candidate.getStatus())));
    }

    private ConfirmationRequestDto toDto(ConfirmationRequestEntity e) {
        WorkItemEntity workItem = e.getWorkItemId() != null
                ? workItemMapper.findById(e.getWorkItemId())
                : null;
        String workflowNodeName = resolveWorkflowNodeName(e.getWorkflowNodeInstanceId());
        return new ConfirmationRequestDto(
                e.getId(),
                ConfirmationRequestType.valueOf(e.getRequestType()),
                ConfirmationStatus.valueOf(e.getStatus()),
                e.getWorkItemId(),
                workItem != null ? workItem.getCode() : null,
                workItem != null ? WorkItemType.valueOf(workItem.getType()) : null,
                workItem != null ? workItem.getTitle() : null,
                e.getWorkflowInstanceId(),
                e.getWorkflowNodeInstanceId(),
                workflowNodeName,
                e.getAgentSessionId(),
                e.getSkillName(),
                e.getTitle(),
                e.getContent(),
                e.getContextSummary(),
                e.getOptionsJson(),
                e.getPriority() != null ? Priority.valueOf(e.getPriority()) : Priority.MEDIUM,
                parseDateTime(e.getCreatedAt()),
                e.getInteractionId(),
                e.getInteractionType(),
                e.getInteractionSchemaJson(),
                e.getInteractionContextJson(),
                e.getInteractionRequired() != null ? e.getInteractionRequired() != 0 : null,
                e.getInteractionOrderNo()
        );
    }

    private String resolveWorkflowNodeName(String nodeInstanceId) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return null;
        }
        var node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            return null;
        }
        var instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        if (instance == null) {
            return null;
        }
        return workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId()).stream()
                .filter(def -> def.getId().equals(node.getNodeDefinitionId()))
                .findFirst()
                .map(WorkflowNodeDefinitionEntity::getName)
                .orElse(null);
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SQLITE_DATETIME).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return OffsetDateTime.parse(value);
        }
    }
}
