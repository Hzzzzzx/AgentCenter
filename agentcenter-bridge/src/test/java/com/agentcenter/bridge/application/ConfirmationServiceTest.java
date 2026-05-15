package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.agentcenter.bridge.application.runtime.translation.QuestionConfirmationHandler;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

class ConfirmationServiceTest {

    private ConfirmationMapper confirmationMapper;
    private WorkflowCommandService workflowCommandService;
    private AgentSessionMapper agentSessionMapper;
    private AgentMessageMapper agentMessageMapper;
    private RuntimeEventService runtimeEventService;
    private RuntimeGateway runtimeGateway;
    private PermissionConfirmationHandler permissionConfirmationHandler;
    private QuestionConfirmationHandler questionConfirmationHandler;
    private ConfirmationService service;

    @BeforeEach
    void setUp() {
        confirmationMapper = mock(ConfirmationMapper.class);
        workflowCommandService = mock(WorkflowCommandService.class);
        WorkItemMapper workItemMapper = mock(WorkItemMapper.class);
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        agentSessionMapper = mock(AgentSessionMapper.class);
        agentMessageMapper = mock(AgentMessageMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        runtimeGateway = mock(RuntimeGateway.class);
        permissionConfirmationHandler = mock(PermissionConfirmationHandler.class);
        questionConfirmationHandler = mock(QuestionConfirmationHandler.class);
        service = new ConfirmationService(
                confirmationMapper,
                workflowCommandService,
                workItemMapper,
                workflowMapper,
                agentSessionMapper,
                agentMessageMapper,
                runtimeEventService,
                runtimeGateway,
                permissionConfirmationHandler,
                questionConfirmationHandler);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resolveQuestionConfirmationRepliesToOpenCodeAfterMarkingResolved() {
        ConfirmationRequestEntity entity = questionConfirmation();
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.CHOOSE,
                "快速验证",
                Map.of("choice", "FAST", "choiceLabel", "快速验证"));

        var result = service.resolve(entity.getId(), request);

        verify(confirmationMapper).update(entity);
        verify(questionConfirmationHandler, never()).respondQuestion(any(), any(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        var inOrder = inOrder(confirmationMapper, questionConfirmationHandler);
        inOrder.verify(confirmationMapper).update(entity);
        inOrder.verify(questionConfirmationHandler)
                .respondQuestion(eq(entity), eq(request), eq(ConfirmationActionType.CHOOSE));

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        verify(permissionConfirmationHandler, never()).respondPermission(any(), any(), eq(true));
        verifyNoInteractions(workflowCommandService);
    }

    @Test
    void resolveQuestionRuntimeFailureAfterCommitKeepsResolvedAndPublishesFailure() {
        ConfirmationRequestEntity entity = questionConfirmation();
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());
        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.CHOOSE,
                "快速验证",
                Map.of("choice", "FAST", "choiceLabel", "快速验证"));
        doThrow(new IllegalStateException("question endpoint failed"))
                .when(questionConfirmationHandler)
                .respondQuestion(eq(entity), eq(request), eq(ConfirmationActionType.CHOOSE));

        var result = service.resolve(entity.getId(), request);

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        assertThat(entity.getStatus()).isEqualTo(ConfirmationStatus.RESOLVED.name());
        verify(confirmationMapper).update(entity);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(runtimeEventService).publishCommittedEvent(argThat(event ->
                RuntimeEventType.ERROR.equals(event.eventType())
                        && event.payloadJson().contains("question.reply.failed")));
        verify(runtimeEventService).publishCommittedEvent(argThat(event ->
                RuntimeEventType.CONFIRMATION_RESOLVED.equals(event.eventType())));
    }

    @Test
    void resolveRetriesWhenSqliteBusySnapshotOccurs() {
        ConfirmationRequestEntity entity = questionConfirmation();
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());
        doThrow(new UncategorizedSQLException(
                        "update",
                        null,
                        new org.sqlite.SQLiteException("[SQLITE_BUSY_SNAPSHOT] database is locked",
                                SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT)))
                .doNothing()
                .when(confirmationMapper).update(entity);

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.CHOOSE,
                "快速验证",
                Map.of("choice", "FAST", "choiceLabel", "快速验证"));

        var result = service.resolve(entity.getId(), request);

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        verify(confirmationMapper, times(2)).update(entity);
    }

    @Test
    void resolveExceptionSupplementResumesWorkflowNode() {
        ConfirmationRequestEntity entity = exceptionConfirmation();
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.SUPPLEMENT,
                "请继续，但只做只读检查",
                Map.of("input", "请继续，但只做只读检查"));

        var result = service.resolve(entity.getId(), request);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        verify(workflowCommandService).resumeNodeAfterInteraction("node-1");
        verify(workflowCommandService, never()).retryNode("node-1");
        verify(workflowCommandService, never()).skipNode("node-1");
    }

    @Test
    void resolveRuntimeExceptionSupplementSendsRecoveryPromptToSession() {
        ConfirmationRequestEntity entity = runtimeExceptionConfirmation();
        AgentSessionEntity session = agentSession("agent-session-1", "ses-old");
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());
        when(agentSessionMapper.findById("agent-session-1")).thenReturn(session);
        when(runtimeGateway.ensureSessionWithContext(eq(RuntimeType.OPENCODE), any(RuntimeOperationContext.class)))
                .thenReturn("ses-old");

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.SUPPLEMENT,
                "继续，但先确认状态",
                Map.of("input", "继续，但先确认状态"));

        var result = service.resolve(entity.getId(), request);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        verify(runtimeGateway).sendMessageWithContext(eq(RuntimeType.OPENCODE), argThat(context ->
                        "agent-session-1".equals(context.agentSessionId())
                                && "ses-old".equals(context.runtimeSessionId())),
                org.mockito.ArgumentMatchers.contains("继续，但先确认状态"));
        verifyNoInteractions(workflowCommandService);
    }

    @Test
    void resolveRuntimeExceptionRetryUsesOriginalUserMessage() {
        ConfirmationRequestEntity entity = runtimeExceptionConfirmation();
        AgentSessionEntity session = agentSession("agent-session-1", "ses-old");
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());
        when(agentSessionMapper.findById("agent-session-1")).thenReturn(session);
        when(runtimeGateway.ensureSessionWithContext(eq(RuntimeType.OPENCODE), any(RuntimeOperationContext.class)))
                .thenReturn("ses-new");

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.RETRY,
                "重试",
                Map.of());

        service.resolve(entity.getId(), request);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(agentSessionMapper).update(session);
        verify(runtimeGateway).sendMessageWithContext(eq(RuntimeType.OPENCODE), argThat(context ->
                        "agent-session-1".equals(context.agentSessionId())
                                && "ses-new".equals(context.runtimeSessionId())),
                org.mockito.ArgumentMatchers.contains("原始用户请求"));
    }

    @Test
    void resolveRuntimeExceptionPublishesFriendlyMessageForInvalidEndpoint() {
        ConfirmationRequestEntity entity = runtimeExceptionConfirmation();
        AgentSessionEntity session = agentSession("agent-session-1", "ses-old");
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());
        when(agentSessionMapper.findById("agent-session-1")).thenReturn(session);
        when(runtimeGateway.ensureSessionWithContext(eq(RuntimeType.OPENCODE), any(RuntimeOperationContext.class)))
                .thenThrow(new RuntimeTransportException(
                        "Invalid OpenCode serve endpoint: baseUrl='?C:\\Program Files\\opencode'",
                        null,
                        false));

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.RETRY,
                "重试",
                Map.of());

        service.resolve(entity.getId(), request);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(runtimeGateway, never()).sendMessageWithContext(any(), any(), any());
        verify(runtimeEventService).publishEvent(argThat(event ->
                RuntimeEventType.ERROR.equals(event.eventType())
                        && event.payloadJson().contains("Runtime 地址配置异常")));
    }

    private ConfirmationRequestEntity questionConfirmation() {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId("question_rt_session_q_1");
        entity.setRequestType(ConfirmationRequestType.DECISION.name());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setWorkflowNodeInstanceId("node-1");
        entity.setAgentSessionId("agent-session-1");
        entity.setRuntimeType("OPENCODE");
        entity.setRuntimeSessionId("rt-session");
        entity.setInteractionId("q-1");
        entity.setInteractionType(QuestionConfirmationHandler.INTERACTION_TYPE);
        entity.setTitle("选择方案");
        entity.setContent("请选择推进方案");
        entity.setPriority("HIGH");
        entity.setCreatedAt("2026-05-11 12:00:00");
        entity.setUpdatedAt("2026-05-11 12:00:00");
        return entity;
    }

    private ConfirmationRequestEntity exceptionConfirmation() {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId("exception_rt_session_1");
        entity.setRequestType(ConfirmationRequestType.EXCEPTION.name());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setWorkflowNodeInstanceId("node-1");
        entity.setAgentSessionId("agent-session-1");
        entity.setRuntimeType("OPENCODE");
        entity.setRuntimeSessionId("rt-session");
        entity.setSkillName("lld-design");
        entity.setTitle("节点执行异常");
        entity.setContent("Agent Runtime 超时，没有返回可用输出");
        entity.setPriority("HIGH");
        entity.setCreatedAt("2026-05-11 12:00:00");
        entity.setUpdatedAt("2026-05-11 12:00:00");
        return entity;
    }

    private ConfirmationRequestEntity runtimeExceptionConfirmation() {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId("runtime_exception_1");
        entity.setRequestType(ConfirmationRequestType.EXCEPTION.name());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setAgentSessionId("agent-session-1");
        entity.setRuntimeType("OPENCODE");
        entity.setRuntimeSessionId("ses-old");
        entity.setTitle("Runtime 执行中断");
        entity.setContent("Runtime sendMessage failed");
        entity.setPriority("HIGH");
        entity.setInteractionType("RUNTIME_EXCEPTION");
        entity.setInteractionContextJson("""
                {"originalUserMessage":"原始用户请求","errorMessage":"Connection refused"}
                """);
        entity.setCreatedAt("2026-05-11 12:00:00");
        entity.setUpdatedAt("2026-05-11 12:00:00");
        return entity;
    }

    private AgentSessionEntity agentSession(String id, String runtimeSessionId) {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(id);
        session.setRuntimeType("OPENCODE");
        session.setRuntimeSessionId(runtimeSessionId);
        return session;
    }
}
