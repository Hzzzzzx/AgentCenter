package com.agentcenter.bridge.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.agentcenter.bridge.application.runtime.translation.QuestionConfirmationHandler;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(ConfirmationService.class);
    private static final int SQLITE_BUSY_MAX_RETRIES = 3;
    private static final long SQLITE_BUSY_RETRY_SLEEP_MS = 80L;
    private static final int LOG_FIELD_LIMIT = 180;

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfirmationMapper confirmationMapper;
    private final WorkflowCommandService workflowCommandService;
    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final RuntimeEventService runtimeEventService;
    private final RuntimeGateway runtimeGateway;
    private final PermissionConfirmationHandler permissionConfirmationHandler;
    private final QuestionConfirmationHandler questionConfirmationHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agentcenter.trace.conversation-log-enabled:false}")
    private boolean conversationLogEnabled;

    public ConfirmationService(ConfirmationMapper confirmationMapper,
                               WorkflowCommandService workflowCommandService,
                               WorkItemMapper workItemMapper,
                               WorkflowMapper workflowMapper,
                               AgentSessionMapper agentSessionMapper,
                               AgentMessageMapper agentMessageMapper,
                               RuntimeEventService runtimeEventService,
                               RuntimeGateway runtimeGateway,
                               PermissionConfirmationHandler permissionConfirmationHandler,
                               QuestionConfirmationHandler questionConfirmationHandler) {
        this.confirmationMapper = confirmationMapper;
        this.workflowCommandService = workflowCommandService;
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.runtimeEventService = runtimeEventService;
        this.runtimeGateway = runtimeGateway;
        this.permissionConfirmationHandler = permissionConfirmationHandler;
        this.questionConfirmationHandler = questionConfirmationHandler;
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
        updateConfirmationWithRetry(entity);
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
                    || ConfirmationActionType.SUPPLEMENT.equals(actionType)
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
            logInvalidConfirmationAction(entity, requestType, actionType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    requestType + " confirmation does not accept " + actionType + ". "
                    + "Valid actions for " + requestType + ": " + validActionsFor(requestType));
        }

        boolean isQuestionConfirmation = QuestionConfirmationHandler.isQuestionConfirmation(entity);
        Runnable questionResponseAfterCommit = isQuestionConfirmation
                ? () -> respondQuestionAfterCommit(entity, request, actionType)
                : null;

        if (ConfirmationRequestType.PERMISSION.equals(requestType)) {
            respondPermissionBeforeResolving(entity, request, actionType);
        }

        if (ConfirmationActionType.REJECT.equals(actionType)) {
            return handleReject(entity, request, questionResponseAfterCommit);
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
        updateConfirmationWithRetry(entity);

        String actionDescription = buildActionDescription(actionType, request);
        var nodeInstanceId = entity.getWorkflowNodeInstanceId();
        boolean shouldDispatchWorkflow = nodeInstanceId != null
                && !isQuestionConfirmation
                && !hasOtherBlockingForNode(entity);
        boolean isDecision = ConfirmationRequestType.DECISION.name().equals(entity.getRequestType());
        boolean isException = ConfirmationRequestType.EXCEPTION.name().equals(entity.getRequestType());
        boolean isSkip = ConfirmationActionType.SKIP.equals(actionType);
        boolean isRuntimeSessionIntervention = isException
                && nodeInstanceId == null
                && entity.getAgentSessionId() != null
                && !entity.getAgentSessionId().isBlank();
        // Only route DECISION as system action for WORKFLOW_ADVANCE confirmations
        boolean isWorkflowAdvance = isDecision
                && "WORKFLOW_ADVANCE".equals(entity.getInteractionType());

        Runnable workflowDispatchAfterCommit = (shouldDispatchWorkflow || isRuntimeSessionIntervention)
                ? () -> {
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
                            if (ConfirmationActionType.SUPPLEMENT.equals(actionType)) {
                                workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                            } else if (isSkip) {
                                workflowCommandService.skipNode(nodeInstanceId);
                            } else {
                                workflowCommandService.retryNode(nodeInstanceId);
                            }
                        } else {
                            workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
                        }
                    } else {
                        dispatchRuntimeSessionIntervention(entity, actionType, request);
                    }
                }
                : null;
        logConfirmationResolved(entity, requestType, actionType,
                confirmationDispatchTarget(
                        actionType,
                        shouldDispatchWorkflow,
                        isRuntimeSessionIntervention,
                        isWorkflowAdvance,
                        isDecision,
                        isException,
                        isSkip));
        publishResolutionEventAfterCommit(entity, actionType, actionDescription,
                combineAfterCommitActions(questionResponseAfterCommit, workflowDispatchAfterCommit));

        return toDto(entity);
    }

    private void respondPermissionBeforeResolving(ConfirmationRequestEntity entity,
                                                  ResolveConfirmationRequest request,
                                                  ConfirmationActionType actionType) {
        try {
            permissionConfirmationHandler.respondPermission(
                    entity.getRuntimeSessionId(),
                    entity.getInteractionId(),
                    permissionReply(request, actionType));
        } catch (Exception e) {
            publishConfirmationResponseFailure(entity, actionType, "permission.reply.failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to respond to OpenCode permission: " + e.getMessage(), e);
        }
    }

    private String permissionReply(ResolveConfirmationRequest request,
                                   ConfirmationActionType actionType) {
        if (ConfirmationActionType.REJECT.equals(actionType)) {
            return "reject";
        }
        String reply = extractPayloadField(request, "reply");
        if ("always".equals(reply) || "reject".equals(reply) || "once".equals(reply)) {
            return reply;
        }
        return "once";
    }

    private void respondQuestionAfterCommit(ConfirmationRequestEntity entity,
                                            ResolveConfirmationRequest request,
                                            ConfirmationActionType actionType) {
        try {
            questionConfirmationHandler.respondQuestion(entity, request, actionType);
        } catch (Exception e) {
            publishCommittedConfirmationResponseFailure(entity, actionType, "question.reply.failed", e);
            log.warn("conversation.question_reply_after_commit status=failed confirmation={} requestId={} error={}",
                    entity.getId(), entity.getInteractionId(), errorMessage(e));
        }
    }

    private ConfirmationRequestDto handleReject(ConfirmationRequestEntity entity,
                                                 ResolveConfirmationRequest request,
                                                 Runnable afterCommitAction) {
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.REJECTED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(request.comment());
        entity.setUpdatedAt(now);
        updateConfirmationWithRetry(entity);

        logConfirmationResolved(entity,
                ConfirmationRequestType.valueOf(entity.getRequestType()),
                ConfirmationActionType.REJECT,
                "rejected");
        publishResolutionEventAfterCommit(entity, ConfirmationActionType.REJECT,
                buildActionDescription(ConfirmationActionType.REJECT, request), afterCommitAction);

        return toDto(entity);
    }

    private Runnable combineAfterCommitActions(Runnable first, Runnable second) {
        if (first == null) return second;
        if (second == null) return first;
        return () -> {
            first.run();
            second.run();
        };
    }

    private String validActionsFor(ConfirmationRequestType requestType) {
        return switch (requestType) {
            case EXCEPTION -> "RETRY, SUPPLEMENT, SKIP, REJECT";
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

    private void dispatchRuntimeSessionIntervention(ConfirmationRequestEntity confirmation,
                                                    ConfirmationActionType actionType,
                                                    ResolveConfirmationRequest request) {
        if (ConfirmationActionType.SKIP.equals(actionType) || ConfirmationActionType.REJECT.equals(actionType)) {
            publishRuntimeInterventionStatus(confirmation, "用户已取消 Runtime 恢复。", false);
            return;
        }

        AgentSessionEntity session = agentSessionMapper.findById(confirmation.getAgentSessionId());
        if (session == null) {
            logRuntimeIntervention(confirmation, actionType, "session_missing", true);
            publishRuntimeInterventionStatus(confirmation, "会话不存在，无法恢复 Runtime。", true);
            return;
        }

        String prompt = buildRuntimeRecoveryPrompt(confirmation, actionType, request);
        if (prompt.isBlank()) {
            logRuntimeIntervention(confirmation, actionType, "empty_prompt", true);
            publishRuntimeInterventionStatus(confirmation, "没有可用于恢复的指令。", true);
            return;
        }

        try {
            RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
            String runtimeSessionId = runtimeGateway.ensureSession(
                    runtimeType, session.getWorkItemId(), session.getId(), session.getRuntimeSessionId());
            if (runtimeSessionId != null && !runtimeSessionId.equals(session.getRuntimeSessionId())) {
                session.setRuntimeSessionId(runtimeSessionId);
                agentSessionMapper.update(session);
            }
            runtimeGateway.sendMessage(runtimeType, runtimeSessionId, prompt);
            logRuntimeIntervention(confirmation, actionType, "sent", false);
            publishRuntimeInterventionStatus(confirmation, "已把恢复指令发送给 Runtime。", false);
        } catch (Exception e) {
            logRuntimeIntervention(confirmation, actionType,
                    "send_failed:" + errorMessage(e), true);
            publishRuntimeInterventionStatus(confirmation,
                    runtimeRecoveryFailureMessage(e),
                    true);
        }
    }

    private String runtimeRecoveryFailureMessage(Throwable error) {
        if (isInvalidOpenCodeEndpoint(error)) {
            return "Runtime 地址配置异常，无法发送恢复指令。请检查 OpenCode serve hostname/port 或 Runtime payload。";
        }
        return "Runtime 恢复指令发送失败：" + errorMessage(error);
    }

    private boolean isInvalidOpenCodeEndpoint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("Invalid OpenCode serve endpoint")
                    || message.contains("Illegal char")
                    || message.contains("Illegal character"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildRuntimeRecoveryPrompt(ConfirmationRequestEntity confirmation,
                                              ConfirmationActionType actionType,
                                              ResolveConfirmationRequest request) {
        String originalUserMessage = extractInteractionContextField(confirmation, "originalUserMessage");
        String errorMessage = extractInteractionContextField(confirmation, "errorMessage");
        String userInput = firstNonBlank(extractPayloadField(request, "input"), request.comment());

        if (ConfirmationActionType.RETRY.equals(actionType)) {
            String retryPrompt = firstNonBlank(originalUserMessage, userInput);
            if (retryPrompt == null || retryPrompt.isBlank()) {
                return "请基于当前会话上下文重试上一轮失败的 Runtime 请求。";
            }
            return """
                    上一轮 Runtime 调用失败，现在执行一次防呆重试。

                    失败原因：%s

                    请重试下面的用户请求；如果你判断可能重复执行有副作用操作，请先说明风险并请求用户确认。

                    用户请求：
                    %s
                    """.formatted(firstNonBlank(errorMessage, "未知错误"), retryPrompt).trim();
        }

        return """
                上一轮 Runtime 调用出现异常，用户已经介入并补充了恢复指令。

                失败原因：%s

                用户补充：
                %s

                请基于当前会话上下文继续处理，不要重复已经完成的步骤；如果存在重复执行风险，请先向用户说明并请求确认。
                """.formatted(firstNonBlank(errorMessage, "未知错误"), firstNonBlank(userInput, "请继续恢复当前任务。")).trim();
    }

    private String extractInteractionContextField(ConfirmationRequestEntity confirmation, String field) {
        String context = confirmation.getInteractionContextJson();
        if (context == null || context.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(context);
            JsonNode value = root.path(field);
            return value.isMissingNode() || value.isNull() ? null : value.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null && !fallback.isBlank() ? fallback : null;
    }

    private void publishRuntimeInterventionStatus(ConfirmationRequestEntity confirmation,
                                                  String message,
                                                  boolean error) {
        if (confirmation.getAgentSessionId() == null || confirmation.getAgentSessionId().isBlank()) {
            return;
        }
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "kind", "runtime_intervention",
                    "status", error ? "failed" : "completed",
                    "message", message,
                    "confirmationId", confirmation.getId()
            ));
        } catch (Exception e) {
            payloadJson = "{\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
        runtimeEventService.publishEvent(new RuntimeEventDto(
                null,
                confirmation.getAgentSessionId(),
                confirmation.getWorkItemId(),
                confirmation.getWorkflowInstanceId(),
                confirmation.getWorkflowNodeInstanceId(),
                error ? RuntimeEventType.ERROR : RuntimeEventType.STATUS,
                RuntimeEventSource.BRIDGE,
                payloadJson,
                null
        ));
    }

    private void publishConfirmationResponseFailure(ConfirmationRequestEntity confirmation,
                                                    ConfirmationActionType actionType,
                                                    String rawEventType,
                                                    Exception error) {
        publishConfirmationResponseFailure(confirmation, actionType, rawEventType, error, false);
    }

    private void publishCommittedConfirmationResponseFailure(ConfirmationRequestEntity confirmation,
                                                             ConfirmationActionType actionType,
                                                             String rawEventType,
                                                             Exception error) {
        publishConfirmationResponseFailure(confirmation, actionType, rawEventType, error, true);
    }

    private void publishConfirmationResponseFailure(ConfirmationRequestEntity confirmation,
                                                    ConfirmationActionType actionType,
                                                    String rawEventType,
                                                    Exception error,
                                                    boolean committed) {
        if (confirmation.getAgentSessionId() == null || confirmation.getAgentSessionId().isBlank()) {
            return;
        }

        String payloadJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", "confirmation_response");
            payload.put("status", "failed");
            payload.put("title", "交互响应失败");
            payload.put("summary", errorMessage(error));
            payload.put("confirmationId", confirmation.getId());
            payload.put("requestType", confirmation.getRequestType());
            payload.put("actionType", actionType.name());
            payload.put("recoverable", true);
            payload.put("rawEventType", rawEventType);
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception serializationError) {
            payloadJson = "{\"kind\":\"confirmation_response\",\"status\":\"failed\",\"confirmationId\":\""
                    + confirmation.getId() + "\"}";
        }

        RuntimeEventDto event = new RuntimeEventDto(
                null,
                confirmation.getAgentSessionId(),
                confirmation.getWorkItemId(),
                confirmation.getWorkflowInstanceId(),
                confirmation.getWorkflowNodeInstanceId(),
                RuntimeEventType.ERROR,
                RuntimeEventSource.BRIDGE,
                payloadJson,
                null
        );
        if (committed) {
            runtimeEventService.publishCommittedEvent(event);
        } else {
            runtimeEventService.publishEvent(event);
        }
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
        String capturedId = entity.getId();
        String sessionId = entity.getAgentSessionId();
        if (sessionId == null) {
            if (afterCommitAction != null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        runAfterCommitAction(capturedId, afterCommitAction);
                    }
                });
            }
            return;
        }

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
        Runnable safeAfterCommit = afterCommitAction != null
                ? () -> {
                    try {
                        afterCommitAction.run();
                    } catch (Exception e) {
                        log.warn("conversation.confirmation_after_commit status=failed confirmation={} error={}",
                                capturedId, errorMessage(e), e);
                        publishAfterCommitFailureEvent(sessionId, capturedWorkItemId,
                                capturedWorkflowInstanceId, capturedNodeInstanceId, capturedId, e);
                    }
                }
                : null;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (safeAfterCommit != null) {
                    safeAfterCommit.run();
                }
                // dispatch must run first — CONFIRMATION_RESOLVED signals "state already updated"
                runtimeEventService.publishCommittedEvent(new RuntimeEventDto(
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

    private void publishAfterCommitFailureEvent(String sessionId, String workItemId,
                                                 String workflowInstanceId, String nodeInstanceId,
                                                 String confirmationId, Exception error) {
        if (sessionId == null || sessionId.isBlank()) return;
        String payloadJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", "confirmation_response");
            payload.put("status", "failed");
            payload.put("title", "提交后恢复失败");
            payload.put("summary", errorMessage(error));
            payload.put("confirmationId", confirmationId);
            payload.put("recoverable", true);
            payload.put("rawEventType", "after_commit_action.failed");
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{\"kind\":\"confirmation_response\",\"status\":\"failed\",\"confirmationId\":\""
                    + confirmationId + "\"}";
        }
        runtimeEventService.publishCommittedEvent(new RuntimeEventDto(
                null, sessionId, workItemId, workflowInstanceId, nodeInstanceId,
                RuntimeEventType.ERROR, RuntimeEventSource.BRIDGE, payloadJson, null
        ));
    }

    private void runAfterCommitAction(String confirmationId, Runnable afterCommitAction) {
        try {
            afterCommitAction.run();
        } catch (Exception e) {
            log.warn("conversation.confirmation_after_commit status=failed confirmation={} error={}",
                    confirmationId, errorMessage(e), e);
        }
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
        updateConfirmationWithRetry(entity);
        return toDto(entity);
    }

    private void updateConfirmationWithRetry(ConfirmationRequestEntity entity) {
        for (int attempt = 1; ; attempt++) {
            try {
                confirmationMapper.update(entity);
                return;
            } catch (UncategorizedSQLException e) {
                if (!isSqliteBusySnapshot(e) || attempt >= SQLITE_BUSY_MAX_RETRIES) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
    }

    private boolean isSqliteBusySnapshot(UncategorizedSQLException e) {
        Throwable cause = e.getCause();
        if (!(cause instanceof org.sqlite.SQLiteException sqliteException)) {
            return false;
        }
        String message = sqliteException.getMessage();
        return sqliteException.getErrorCode() == 5
                && message != null
                && message.contains("SQLITE_BUSY_SNAPSHOT");
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = SQLITE_BUSY_RETRY_SLEEP_MS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
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

    private String confirmationDispatchTarget(ConfirmationActionType actionType,
                                              boolean shouldDispatchWorkflow,
                                              boolean runtimeSessionIntervention,
                                              boolean workflowAdvance,
                                              boolean decision,
                                              boolean exception,
                                              boolean skip) {
        if (runtimeSessionIntervention) {
            return "runtime_session_intervention";
        }
        if (!shouldDispatchWorkflow) {
            return "none";
        }
        if (workflowAdvance) {
            return switch (actionType) {
                case ADVANCE -> "workflow_advance";
                case SUPPLEMENT -> "workflow_resume";
                case RETRY -> "workflow_retry";
                default -> "workflow_resume";
            };
        }
        if (decision) {
            return "workflow_resume";
        }
        if (exception) {
            if (ConfirmationActionType.SUPPLEMENT.equals(actionType)) {
                return "workflow_resume";
            }
            if (skip) {
                return "workflow_skip";
            }
            return "workflow_retry";
        }
        return "workflow_resume";
    }

    private void logInvalidConfirmationAction(ConfirmationRequestEntity entity,
                                              ConfirmationRequestType requestType,
                                              ConfirmationActionType actionType) {
        log.warn("conversation.confirmation_invalid_action confirmation={} type={} action={} session={} workItem={} workflow={} node={} validActions={}",
                entity.getId(),
                requestType,
                actionType,
                entity.getAgentSessionId(),
                entity.getWorkItemId(),
                entity.getWorkflowInstanceId(),
                entity.getWorkflowNodeInstanceId(),
                validActionsFor(requestType));
    }

    private void logConfirmationResolved(ConfirmationRequestEntity entity,
                                         ConfirmationRequestType requestType,
                                         ConfirmationActionType actionType,
                                         String dispatchTarget) {
        traceInfo("conversation.confirmation_resolved confirmation={} type={} action={} status={} dispatch={} session={} workItem={} workflow={} node={} runtimeSession={}",
                entity.getId(),
                requestType,
                actionType,
                entity.getStatus(),
                dispatchTarget,
                entity.getAgentSessionId(),
                entity.getWorkItemId(),
                entity.getWorkflowInstanceId(),
                entity.getWorkflowNodeInstanceId(),
                entity.getRuntimeSessionId());
    }

    private void logRuntimeIntervention(ConfirmationRequestEntity confirmation,
                                        ConfirmationActionType actionType,
                                        String status,
                                        boolean error) {
        if (error) {
            log.warn("conversation.runtime_intervention confirmation={} action={} status={} session={} workItem={} workflow={} node={} runtimeType={} runtimeSession={}",
                    confirmation.getId(),
                    actionType,
                    clipForLog(status),
                    confirmation.getAgentSessionId(),
                    confirmation.getWorkItemId(),
                    confirmation.getWorkflowInstanceId(),
                    confirmation.getWorkflowNodeInstanceId(),
                    confirmation.getRuntimeType(),
                    confirmation.getRuntimeSessionId());
            return;
        }
        traceInfo("conversation.runtime_intervention confirmation={} action={} status={} session={} workItem={} workflow={} node={} runtimeType={} runtimeSession={}",
                confirmation.getId(),
                actionType,
                clipForLog(status),
                confirmation.getAgentSessionId(),
                confirmation.getWorkItemId(),
                confirmation.getWorkflowInstanceId(),
                confirmation.getWorkflowNodeInstanceId(),
                confirmation.getRuntimeType(),
                confirmation.getRuntimeSessionId());
    }

    private void traceInfo(String message, Object... args) {
        if (conversationLogEnabled) {
            log.info(message, args);
            return;
        }
        log.debug(message, args);
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? clipForLog(error.getMessage())
                : error.getClass().getSimpleName();
    }

    private String clipForLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= LOG_FIELD_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, LOG_FIELD_LIMIT) + "...";
    }
}
