package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import com.agentcenter.bridge.application.runtime.translation.PermissionConfirmationHandler;
import com.agentcenter.bridge.application.runtime.translation.QuestionConfirmationHandler;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

class ConfirmationServiceTest {

    private ConfirmationMapper confirmationMapper;
    private WorkflowCommandService workflowCommandService;
    private AgentMessageMapper agentMessageMapper;
    private PermissionConfirmationHandler permissionConfirmationHandler;
    private QuestionConfirmationHandler questionConfirmationHandler;
    private ConfirmationService service;

    @BeforeEach
    void setUp() {
        confirmationMapper = mock(ConfirmationMapper.class);
        workflowCommandService = mock(WorkflowCommandService.class);
        WorkItemMapper workItemMapper = mock(WorkItemMapper.class);
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        agentMessageMapper = mock(AgentMessageMapper.class);
        RuntimeEventService runtimeEventService = mock(RuntimeEventService.class);
        permissionConfirmationHandler = mock(PermissionConfirmationHandler.class);
        questionConfirmationHandler = mock(QuestionConfirmationHandler.class);
        service = new ConfirmationService(
                confirmationMapper,
                workflowCommandService,
                workItemMapper,
                workflowMapper,
                agentMessageMapper,
                runtimeEventService,
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
    void resolveQuestionConfirmationRepliesToOpenCodeWithoutResumingWorkflowNode() {
        ConfirmationRequestEntity entity = questionConfirmation();
        when(confirmationMapper.findById(entity.getId())).thenReturn(entity);
        when(agentMessageMapper.findBySessionId(entity.getAgentSessionId())).thenReturn(List.of());

        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.CHOOSE,
                "快速验证",
                Map.of("choice", "FAST", "choiceLabel", "快速验证"));

        var result = service.resolve(entity.getId(), request);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(result.status()).isEqualTo(ConfirmationStatus.RESOLVED);
        verify(questionConfirmationHandler).respondQuestion(eq(entity), eq(request), eq(ConfirmationActionType.CHOOSE));
        verify(permissionConfirmationHandler, never()).respondPermission(any(), any(), eq(true));
        verify(confirmationMapper).update(entity);
        verifyNoInteractions(workflowCommandService);
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
}
