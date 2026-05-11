package com.agentcenter.bridge.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.AgentMessageDto;
import com.agentcenter.bridge.api.dto.AgentSessionDto;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.api.dto.SendMessageRequest;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.domain.session.SessionStatus;
import com.agentcenter.bridge.domain.session.SessionType;
import com.agentcenter.bridge.domain.workflow.WorkflowStatus;
import com.agentcenter.bridge.domain.workflow.WorkflowUserAction;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

import jakarta.annotation.PreDestroy;

@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);
    private static final AtomicInteger MESSAGE_THREAD_COUNTER = new AtomicInteger(1);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CONTINUE_CURRENT_RUNTIME_PROMPT =
            "请继续当前节点未完成的回答，不要重新开始节点，也不要重复发送或复述工作流节点提示词。";
    private static final int DEFAULT_SAFE_AUTO_RETRY_LIMIT = 2;
    private static final long DEFAULT_SAFE_AUTO_RETRY_BACKOFF_MS = 700L;
    private static final String RUNTIME_EXCEPTION_INTERACTION = "RUNTIME_EXCEPTION";

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ConfirmationMapper confirmationMapper;
    private final RuntimeEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final RuntimeGateway runtimeGateway;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final RuntimeEventService runtimeEventService;
    private final WorkflowCommandService workflowCommandService;
    private final WorkflowMapper workflowMapper;
    private final int safeAutoRetryLimit;
    private final long safeAutoRetryBackoffMs;
    private final ExecutorService messageExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agentcenter-message-" + MESSAGE_THREAD_COUNTER.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public AgentSessionService(AgentSessionMapper sessionMapper,
                               AgentMessageMapper messageMapper,
                               ConfirmationMapper confirmationMapper,
                               RuntimeEventMapper eventMapper,
                               IdGenerator idGenerator,
                               RuntimeGateway runtimeGateway,
                               WebSocketSessionRegistry webSocketSessionRegistry,
                               RuntimeEventService runtimeEventService,
                               @Lazy WorkflowCommandService workflowCommandService,
                               WorkflowMapper workflowMapper,
                               @Value("${agentcenter.runtime.guard.retry-limit:2}") int safeAutoRetryLimit,
                               @Value("${agentcenter.runtime.guard.retry-backoff-ms:700}") long safeAutoRetryBackoffMs) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.confirmationMapper = confirmationMapper;
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
        this.runtimeGateway = runtimeGateway;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.runtimeEventService = runtimeEventService;
        this.workflowCommandService = workflowCommandService;
        this.workflowMapper = workflowMapper;
        this.safeAutoRetryLimit = normalizeRetryLimit(safeAutoRetryLimit);
        this.safeAutoRetryBackoffMs = normalizeRetryBackoffMs(safeAutoRetryBackoffMs);
    }

    @PreDestroy
    public void shutdown() {
        messageExecutor.shutdownNow();
    }

    public List<AgentSessionDto> listSessions() {
        return sessionMapper.findAll().stream()
                .map(this::toSessionDto)
                .toList();
    }

    public AgentSessionDto getSession(String id) {
        AgentSessionEntity entity = sessionMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id);
        }
        return toSessionDto(entity);
    }

    public AgentSessionDto createSession(SessionType sessionType, String title,
                                          String workItemId, String workflowInstanceId,
                                          RuntimeType runtimeType, String workingDirectory) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setId(idGenerator.nextId());
        entity.setSessionType(sessionType.name());
        entity.setTitle(title);
        entity.setWorkItemId(workItemId);
        entity.setWorkflowInstanceId(workflowInstanceId);
        entity.setRuntimeType(runtimeType.name());
        entity.setWorkingDirectory(workingDirectory);
        entity.setStatus(SessionStatus.ACTIVE.name());
        entity.setCreatedBy("system");
        sessionMapper.insert(entity);
        return toSessionDto(entity);
    }

    public AgentSessionDto createSession(SessionType sessionType, String title,
                                          String workItemId, String workflowInstanceId,
                                          RuntimeType runtimeType) {
        return createSession(sessionType, title, workItemId, workflowInstanceId, runtimeType, null);
    }

    public AgentSessionDto bindRuntimeSession(String sessionId, String runtimeSessionId, RuntimeType runtimeType) {
        AgentSessionEntity entity = sessionMapper.findById(sessionId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setRuntimeType(runtimeType.name());
        sessionMapper.update(entity);
        return toSessionDto(entity);
    }

    public List<AgentMessageDto> getMessages(String sessionId) {
        return messageMapper.findBySessionId(sessionId).stream()
                .map(this::toMessageDto)
                .toList();
    }

    public AgentMessageDto sendMessage(String sessionId, SendMessageRequest request) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }

        List<AgentMessageEntity> existing = messageMapper.findBySessionId(sessionId);
        int nextSeqNo = existing.stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;

        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setId(idGenerator.nextId());
        entity.setSessionId(sessionId);
        entity.setRole(MessageRole.USER.name());
        entity.setContent(request.content());
        entity.setContentFormat(request.contentFormat().name());
        entity.setStatus(MessageStatus.COMPLETED.name());
        entity.setSeqNo(nextSeqNo);
        entity.setCreatedBy("system");
        messageMapper.insert(entity);

        if (session.getWorkflowInstanceId() != null && !session.getWorkflowInstanceId().isBlank()) {
            routeWorkflowMessage(session, request);
        } else {
            messageExecutor.submit(() -> dispatchToRuntime(sessionId, request.content()));
        }
        return toMessageDto(entity);
    }

    private void routeWorkflowMessage(AgentSessionEntity session, SendMessageRequest request) {
        String workflowInstanceId = session.getWorkflowInstanceId();
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(workflowInstanceId);
        if (instance == null) {
            log.warn("Workflow instance {} not found for session {}, falling back to runtime dispatch",
                    workflowInstanceId, session.getId());
            messageExecutor.submit(() -> dispatchToRuntime(session.getId(), request.content()));
            return;
        }

        String nodeInstanceId = resolveNodeInstanceId(instance, request);

        if (request.workflowUserAction() != null && !request.workflowUserAction().isBlank()) {
            WorkflowUserAction action = WorkflowUserAction.valueOf(request.workflowUserAction());
            switch (action) {
                case CONTINUE_CURRENT -> continueCurrentRuntime(session, instance, nodeInstanceId, request.content());
                case ADVANCE_NEXT -> workflowCommandService.completeNodeAndScheduleAdvance(nodeInstanceId);
                case RERUN_NODE -> workflowCommandService.retryNode(nodeInstanceId);
                case SKIP_NODE -> workflowCommandService.skipNode(nodeInstanceId);
                case PAUSE_WORKFLOW -> {
                    String now = java.time.LocalDateTime.now().format(SQLITE_DATETIME);
                    instance.setStatus(WorkflowStatus.BLOCKED.name());
                    instance.setUpdatedAt(now);
                    workflowMapper.updateInstance(instance);
                }
            }
        } else {
            if (nodeInstanceId != null) {
                workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId);
            } else {
                messageExecutor.submit(() -> dispatchToRuntime(session.getId(), request.content()));
            }
        }
    }

    private String resolveNodeInstanceId(WorkflowInstanceEntity instance, SendMessageRequest request) {
        if (request.workflowNodeInstanceId() != null && !request.workflowNodeInstanceId().isBlank()) {
            return request.workflowNodeInstanceId();
        }
        return instance.getCurrentNodeInstanceId();
    }

    private void continueCurrentRuntime(AgentSessionEntity session,
                                        WorkflowInstanceEntity instance,
                                        String nodeInstanceId,
                                        String requestedPrompt) {
        String now = java.time.LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
        runtimeGateway.registerWorkflowNodeContext(
                runtimeType, session.getId(), instance.getWorkItemId(), instance.getId(), nodeInstanceId);
        String prompt = continueCurrentPrompt(requestedPrompt);
        messageExecutor.submit(() -> dispatchToRuntime(session.getId(), prompt));
    }

    private String continueCurrentPrompt(String requestedPrompt) {
        if (requestedPrompt == null || requestedPrompt.isBlank() || "[CONTINUE_CURRENT]".equals(requestedPrompt)) {
            return CONTINUE_CURRENT_RUNTIME_PROMPT;
        }
        return requestedPrompt;
    }

    public void cancelRuntime(String sessionId) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }

        RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
        if (runtimeType != RuntimeType.MOCK && session.getRuntimeSessionId() != null
                && !session.getRuntimeSessionId().isBlank()) {
            runtimeGateway.cancel(runtimeType, session.getRuntimeSessionId());
        }

        RuntimeEventDto event = new RuntimeEventDto(
                null, sessionId, session.getWorkItemId(), session.getWorkflowInstanceId(), null,
                RuntimeEventType.STATUS, RuntimeEventSource.BRIDGE,
                "{\"status\":\"idle\",\"label\":\"用户已请求暂停当前回复\"}", null);
        try {
            runtimeEventService.publishEvent(event);
        } catch (Exception e) {
            log.debug("Failed to publish cancel runtime event for {}", sessionId, e);
        }
    }

    private void dispatchToRuntime(String sessionId, String content) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            return;
        }

        RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
        if (runtimeType == RuntimeType.MOCK) {
            recordSendMessageEvent(sessionId, session, true, null, content, 0, "NONE");
            broadcastMessages(sessionId);
            return;
        }

        RuntimeDispatchResult dispatchResult = dispatchToRuntimeWithGuard(session, runtimeType, content);
        String effectiveRuntimeSessionId = dispatchResult.runtimeSessionId();
        boolean runtimeOk = dispatchResult.ok();
        String runtimeError = dispatchResult.errorMessage();

        recordSendMessageEvent(sessionId, session, runtimeOk, runtimeError, content,
                dispatchResult.retryCount(), dispatchResult.recoveryMode());

        if (!runtimeOk && runtimeError != null) {
            insertRuntimeErrorMessage(sessionId, effectiveRuntimeSessionId, runtimeError);
            createRuntimeExceptionConfirmation(session, effectiveRuntimeSessionId, content,
                    runtimeError, dispatchResult.retryCount(), dispatchResult.recoveryMode());
        }

        broadcastMessages(sessionId);
    }

    private RuntimeDispatchResult dispatchToRuntimeWithGuard(AgentSessionEntity session,
                                                             RuntimeType runtimeType,
                                                             String content) {
        String runtimeSessionId = session.getRuntimeSessionId();
        int retryCount = 0;
        String lastError = null;

        for (int attempt = 0; attempt <= safeAutoRetryLimit; attempt++) {
            if (attempt > 0) {
                retryCount++;
                sleepBeforeSafeRetry(attempt);
            }
            try {
                runtimeSessionId = runtimeGateway.ensureSession(
                        runtimeType, session.getWorkItemId(), session.getId(), runtimeSessionId);
                if (runtimeSessionId != null && !runtimeSessionId.equals(session.getRuntimeSessionId())) {
                    session.setRuntimeSessionId(runtimeSessionId);
                    sessionMapper.update(session);
                }
                runtimeGateway.sendMessage(runtimeType, runtimeSessionId, content);
                return RuntimeDispatchResult.ok(runtimeSessionId, retryCount);
            } catch (Exception error) {
                lastError = errorMessage(error);
                if (!isSafeAutoRetryable(lastError) || attempt >= safeAutoRetryLimit) {
                    break;
                }
                try {
                    runtimeSessionId = runtimeGateway.createSession(runtimeType, session.getWorkItemId(), session.getId());
                    session.setRuntimeSessionId(runtimeSessionId);
                    sessionMapper.update(session);
                } catch (Exception createError) {
                    lastError = "Runtime recovery session creation failed: " + errorMessage(createError);
                }
            }
        }

        return RuntimeDispatchResult.failed(runtimeSessionId,
                "Runtime sendMessage failed after " + retryCount + " guarded retry attempt(s): " + lastError,
                retryCount,
                retryCount > 0 ? "SAFE_AUTO_EXHAUSTED" : "USER_INTERVENTION_REQUIRED");
    }

    private void recordSendMessageEvent(String sessionId,
                                        AgentSessionEntity session,
                                        boolean runtimeOk,
                                        String runtimeError,
                                        String content,
                                        int retryCount,
                                        String recoveryMode) {
        RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
        String payloadJson = """
                {"action":"sendMessage","runtimeType":"%s","runtimeOk":%s,"runtimeError":"%s","content":"%s","retryCount":%d,"recoveryMode":"%s"}
                """.formatted(runtimeType.name(), runtimeOk, jsonEscape(runtimeError), jsonEscape(content),
                retryCount, jsonEscape(recoveryMode)).trim();
        RuntimeEventDto event = new RuntimeEventDto(
                null, sessionId, session.getWorkItemId(), session.getWorkflowInstanceId(), null,
                RuntimeEventType.STATUS, RuntimeEventSource.BRIDGE, payloadJson, null);
        try {
            runtimeEventService.publishEvent(event);
        } catch (Exception e) {
            log.debug("Failed to publish sendMessage runtime event for {}", sessionId, e);
        }
    }

    private void createRuntimeExceptionConfirmation(AgentSessionEntity session,
                                                    String runtimeSessionId,
                                                    String originalContent,
                                                    String runtimeError,
                                                    int retryCount,
                                                    String recoveryMode) {
        String now = java.time.LocalDateTime.now().format(SQLITE_DATETIME);
        WorkflowInstanceEntity instance = null;
        String nodeInstanceId = null;
        if (session.getWorkflowInstanceId() != null && !session.getWorkflowInstanceId().isBlank()) {
            instance = workflowMapper.findInstanceById(session.getWorkflowInstanceId());
            if (instance != null) {
                nodeInstanceId = instance.getCurrentNodeInstanceId();
                instance.setStatus(WorkflowStatus.BLOCKED.name());
                instance.setUpdatedAt(now);
                workflowMapper.updateInstance(instance);
            }
        }

        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(idGenerator.nextId());
        confirmation.setRequestType(ConfirmationRequestType.EXCEPTION.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setWorkItemId(session.getWorkItemId());
        confirmation.setWorkflowInstanceId(session.getWorkflowInstanceId());
        confirmation.setWorkflowNodeInstanceId(nodeInstanceId);
        confirmation.setAgentSessionId(session.getId());
        confirmation.setRuntimeType(session.getRuntimeType());
        confirmation.setRuntimeSessionId(runtimeSessionId);
        confirmation.setTitle("Runtime 执行中断，需要你介入");
        confirmation.setContent(buildRuntimeExceptionContent(runtimeError, retryCount));
        confirmation.setContextSummary("Runtime 调用异常，Bridge 已完成安全自动重试；继续前需要用户选择恢复方式。");
        confirmation.setOptionsJson("[{\"value\":\"SUPPLEMENT\",\"label\":\"补充指令继续\"},"
                + "{\"value\":\"RETRY\",\"label\":\"防呆重试\"},"
                + "{\"value\":\"SKIP\",\"label\":\"跳过当前节点\"},"
                + "{\"value\":\"REJECT\",\"label\":\"取消恢复\"}]");
        confirmation.setPriority(Priority.HIGH.name());
        confirmation.setInteractionType(RUNTIME_EXCEPTION_INTERACTION);
        confirmation.setInteractionRequired(1);
        confirmation.setInteractionContextJson("""
                {"failureCategory":"TRANSPORT_OR_RUNTIME","retryPolicy":"USER_CONFIRM","retryCount":%d,"recoveryMode":"%s","originalUserMessage":"%s","errorMessage":"%s"}
                """.formatted(retryCount, jsonEscape(recoveryMode), jsonEscape(originalContent), jsonEscape(runtimeError)).trim());
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        boolean deduplicated = upsertRuntimeExceptionConfirmation(confirmation);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null,
                session.getId(),
                session.getWorkItemId(),
                session.getWorkflowInstanceId(),
                nodeInstanceId,
                RuntimeEventType.CONFIRMATION_CREATED,
                RuntimeEventSource.BRIDGE,
                "{\"confirmationId\":\"" + confirmation.getId() + "\",\"requestType\":\"EXCEPTION\",\"interactionType\":\""
                        + RUNTIME_EXCEPTION_INTERACTION + "\",\"deduplicated\":" + deduplicated + "}",
                null
        ));
    }

    boolean upsertRuntimeExceptionConfirmation(ConfirmationRequestEntity confirmation) {
        ConfirmationRequestEntity existing = confirmationMapper.findPendingRuntimeExceptionBySessionId(
                confirmation.getAgentSessionId());
        if (existing == null) {
            confirmationMapper.insert(confirmation);
            return false;
        }

        confirmation.setId(existing.getId());
        confirmation.setCreatedAt(existing.getCreatedAt());
        confirmationMapper.updateRuntimeIntervention(confirmation);
        return true;
    }

    private String buildRuntimeExceptionContent(String runtimeError, int retryCount) {
        return """
                Runtime 执行中断，但会话仍可恢复。

                失败原因：%s
                已自动安全重试：%d 次

                你可以补充一句指令让 Agent 基于当前上下文继续；也可以发起防呆重试、跳过当前节点，或取消本次恢复。
                """.formatted(runtimeError, retryCount).trim();
    }

    static boolean isSafeAutoRetryable(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("connection")
                || normalized.contains("refused")
                || normalized.contains("reset")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || isHttp5xxFailure(normalized)
                || normalized.contains("interrupted")
                || normalized.contains("serve")
                || normalized.contains("超时")
                || normalized.contains("连接");
    }

    private static boolean isHttp5xxFailure(String normalized) {
        return normalized.contains("http 5")
                || normalized.contains("status 5")
                || normalized.contains(" 500")
                || normalized.contains(" 502")
                || normalized.contains(" 503")
                || normalized.contains(" 504")
                || normalized.contains(" 5xx");
    }

    private String errorMessage(Exception error) {
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }

    private void sleepBeforeSafeRetry(int attempt) {
        try {
            Thread.sleep(safeAutoRetryBackoffMs * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static int normalizeRetryLimit(int configuredLimit) {
        return configuredLimit >= 0 ? configuredLimit : DEFAULT_SAFE_AUTO_RETRY_LIMIT;
    }

    static long normalizeRetryBackoffMs(long configuredBackoffMs) {
        return configuredBackoffMs >= 0 ? configuredBackoffMs : DEFAULT_SAFE_AUTO_RETRY_BACKOFF_MS;
    }

    private void insertRuntimeErrorMessage(String sessionId, String runtimeSessionId, String runtimeError) {
        AgentMessageEntity errorMsg = new AgentMessageEntity();
        errorMsg.setId(idGenerator.nextId());
        errorMsg.setSessionId(sessionId);
        errorMsg.setRole(MessageRole.ASSISTANT.name());
        errorMsg.setContent("Runtime 调用失败：" + runtimeError);
        errorMsg.setContentFormat(ContentFormat.TEXT.name());
        errorMsg.setStatus(MessageStatus.COMPLETED.name());
        errorMsg.setSeqNo(nextMessageSeqNo(sessionId));
        errorMsg.setRuntimeMessageId(runtimeSessionId);
        errorMsg.setCreatedBy("runtime");
        messageMapper.insert(errorMsg);
    }

    private int nextMessageSeqNo(String sessionId) {
        return messageMapper.findBySessionId(sessionId).stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;
    }

    private void broadcastMessages(String sessionId) {
        try {
            webSocketSessionRegistry.sendToSession(sessionId, Map.of(
                    "type", "session.messages",
                    "payload", Map.of(
                            "sessionId", sessionId,
                            "messages", getMessages(sessionId)
                    )
            ));
        } catch (Exception e) {
            log.debug("Failed to broadcast session message snapshot for {}", sessionId, e);
        }
    }

    public void recordEvent(RuntimeEventDto event) {
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.setId(event.id() != null ? event.id() : idGenerator.nextId());
        entity.setSessionId(event.sessionId());
        entity.setWorkItemId(event.workItemId());
        entity.setWorkflowInstanceId(event.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(event.workflowNodeInstanceId());
        entity.setEventType(event.eventType().name());
        entity.setEventSource(event.eventSource().name());
        entity.setPayloadJson(event.payloadJson());
        eventMapper.insert(entity);
    }

    private AgentSessionDto toSessionDto(AgentSessionEntity e) {
        return new AgentSessionDto(
                e.getId(),
                SessionType.valueOf(e.getSessionType()),
                e.getTitle(),
                e.getWorkItemId(),
                e.getWorkflowInstanceId(),
                RuntimeType.valueOf(e.getRuntimeType()),
                SessionStatus.valueOf(e.getStatus()),
                e.getWorkingDirectory(),
                parseDateTime(e.getCreatedAt())
        );
    }

    private AgentMessageDto toMessageDto(AgentMessageEntity e) {
        return new AgentMessageDto(
                e.getId(),
                e.getSessionId(),
                MessageRole.valueOf(e.getRole()),
                e.getContent(),
                ContentFormat.valueOf(e.getContentFormat()),
                MessageStatus.valueOf(e.getStatus()),
                e.getSeqNo() != null ? e.getSeqNo() : 0,
                parseDateTime(e.getCreatedAt()),
                e.getWorkflowNodeInstanceId()
        );
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            // SQLite may store without timezone — try as local datetime
            return OffsetDateTime.parse(value.replace(" ", "T") + "+00:00",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record RuntimeDispatchResult(boolean ok,
                                         String runtimeSessionId,
                                         String errorMessage,
                                         int retryCount,
                                         String recoveryMode) {
        static RuntimeDispatchResult ok(String runtimeSessionId, int retryCount) {
            return new RuntimeDispatchResult(true, runtimeSessionId, null, retryCount,
                    retryCount > 0 ? "SAFE_AUTO_RECOVERED" : "NONE");
        }

        static RuntimeDispatchResult failed(String runtimeSessionId, String errorMessage,
                                            int retryCount, String recoveryMode) {
            return new RuntimeDispatchResult(false, runtimeSessionId, errorMessage, retryCount, recoveryMode);
        }
    }
}
