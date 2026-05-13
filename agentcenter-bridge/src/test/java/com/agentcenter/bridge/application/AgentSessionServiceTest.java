package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

class AgentSessionServiceTest {

    @Test
    void safeAutoRetryableIncludesTransportAndHttp5xxFailures() {
        assertThat(AgentSessionService.isSafeAutoRetryable("Connection refused")).isTrue();
        assertThat(AgentSessionService.isSafeAutoRetryable("HTTP 503 Service Unavailable")).isTrue();
        assertThat(AgentSessionService.isSafeAutoRetryable("请求超时")).isTrue();
    }

    @Test
    void safeAutoRetryableDoesNotTreatAnyDigitFiveAsRetryable() {
        assertThat(AgentSessionService.isSafeAutoRetryable("validation failed for step 5")).isFalse();
        assertThat(AgentSessionService.isSafeAutoRetryable("permission denied after 5 attempts")).isFalse();
    }

    @Test
    void retryGuardConfigFallsBackWhenNegative() {
        assertThat(AgentSessionService.normalizeRetryLimit(-1)).isEqualTo(2);
        assertThat(AgentSessionService.normalizeRetryBackoffMs(-1)).isEqualTo(700L);
    }

    @Test
    void retryGuardConfigAllowsZeroToDisableAutoRetryOrBackoff() {
        assertThat(AgentSessionService.normalizeRetryLimit(0)).isZero();
        assertThat(AgentSessionService.normalizeRetryBackoffMs(0)).isZero();
    }

    @Test
    void upsertRuntimeExceptionConfirmationInsertsWhenNoPendingInterventionExists() {
        ConfirmationMapper confirmationMapper = mock(ConfirmationMapper.class);
        AgentSessionService service = serviceWith(confirmationMapper);
        ConfirmationRequestEntity confirmation = runtimeExceptionConfirmation("new-id");

        boolean deduplicated = service.upsertRuntimeExceptionConfirmation(confirmation);

        assertThat(deduplicated).isFalse();
        verify(confirmationMapper).insert(confirmation);
        verify(confirmationMapper, never()).updateRuntimeIntervention(confirmation);
    }

    @Test
    void upsertRuntimeExceptionConfirmationUpdatesExistingPendingIntervention() {
        ConfirmationMapper confirmationMapper = mock(ConfirmationMapper.class);
        ConfirmationRequestEntity existing = runtimeExceptionConfirmation("existing-id");
        existing.setCreatedAt("2026-05-11 12:00:00");
        when(confirmationMapper.findPendingRuntimeExceptionBySessionId("agent-session-1")).thenReturn(existing);
        AgentSessionService service = serviceWith(confirmationMapper);
        ConfirmationRequestEntity replacement = runtimeExceptionConfirmation("new-id");
        replacement.setContent("new runtime error");

        boolean deduplicated = service.upsertRuntimeExceptionConfirmation(replacement);

        assertThat(deduplicated).isTrue();
        assertThat(replacement.getId()).isEqualTo("existing-id");
        assertThat(replacement.getCreatedAt()).isEqualTo("2026-05-11 12:00:00");
        verify(confirmationMapper, never()).insert(replacement);
        verify(confirmationMapper).updateRuntimeIntervention(replacement);
    }

    @Test
    void runtimeExceptionConfirmationPublishesCompleteCreatedPayload() throws Exception {
        ConfirmationMapper confirmationMapper = mock(ConfirmationMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        RuntimeEventService runtimeEventService = mock(RuntimeEventService.class);
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        ConfirmationCreatedEventPayloadBuilder payloadBuilder = mock(ConfirmationCreatedEventPayloadBuilder.class);
        when(idGenerator.nextId()).thenReturn("conf-runtime");
        when(payloadBuilder.buildPayload(any(ConfirmationRequestEntity.class), eq(Map.<String, Object>of("deduplicated", false))))
                .thenReturn("{\"id\":\"conf-runtime\",\"confirmationId\":\"conf-runtime\",\"requestType\":\"EXCEPTION\",\"status\":\"PENDING\",\"title\":\"Runtime 执行中断，需要你介入\"}");
        AgentSessionService service = serviceWith(confirmationMapper, idGenerator, runtimeEventService, workflowMapper, payloadBuilder);
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId("agent-session-1");
        session.setWorkItemId("work-1");
        session.setRuntimeType("OPENCODE");

        Method method = AgentSessionService.class.getDeclaredMethod(
                "createRuntimeExceptionConfirmation",
                AgentSessionEntity.class,
                String.class,
                String.class,
                String.class,
                int.class,
                String.class);
        method.setAccessible(true);
        method.invoke(service, session, "runtime-session-1", "continue", "HTTP 503 Service Unavailable", 2, "SAFE_AUTO_RETRY_EXHAUSTED");

        ArgumentCaptor<ConfirmationRequestEntity> confirmationCaptor = ArgumentCaptor.forClass(ConfirmationRequestEntity.class);
        verify(payloadBuilder).buildPayload(confirmationCaptor.capture(), eq(Map.<String, Object>of("deduplicated", false)));
        assertThat(confirmationCaptor.getValue().getId()).isEqualTo("conf-runtime");
        assertThat(confirmationCaptor.getValue().getTitle()).isEqualTo("Runtime 执行中断，需要你介入");
        assertThat(confirmationCaptor.getValue().getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RuntimeEventType.CONFIRMATION_CREATED);
        assertThat(eventCaptor.getValue().payloadJson()).contains("\"status\":\"PENDING\"");
        assertThat(eventCaptor.getValue().payloadJson()).contains("\"title\":\"Runtime 执行中断，需要你介入\"");
    }

    private AgentSessionService serviceWith(ConfirmationMapper confirmationMapper) {
        return serviceWith(
                confirmationMapper,
                mock(IdGenerator.class),
                mock(RuntimeEventService.class),
                mock(WorkflowMapper.class),
                mock(ConfirmationCreatedEventPayloadBuilder.class));
    }

    private AgentSessionService serviceWith(ConfirmationMapper confirmationMapper,
                                            IdGenerator idGenerator,
                                            RuntimeEventService runtimeEventService,
                                            WorkflowMapper workflowMapper,
                                            ConfirmationCreatedEventPayloadBuilder confirmationCreatedEventPayloadBuilder) {
        return new AgentSessionService(
                mock(AgentSessionMapper.class),
                mock(AgentMessageMapper.class),
                confirmationMapper,
                mock(RuntimeEventMapper.class),
                idGenerator,
                mock(RuntimeGateway.class),
                mock(WebSocketSessionRegistry.class),
                runtimeEventService,
                mock(WorkflowCommandService.class),
                workflowMapper,
                mock(SkillRegistryService.class),
                mock(RuntimeResourceService.class),
                mock(McpRegistryService.class),
                mock(WorkItemMapper.class),
                confirmationCreatedEventPayloadBuilder,
                2,
                0);
    }

    private ConfirmationRequestEntity runtimeExceptionConfirmation(String id) {
        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(id);
        confirmation.setRequestType("EXCEPTION");
        confirmation.setStatus("PENDING");
        confirmation.setAgentSessionId("agent-session-1");
        confirmation.setRuntimeType("OPENCODE");
        confirmation.setRuntimeSessionId("ses-1");
        confirmation.setInteractionType("RUNTIME_EXCEPTION");
        confirmation.setInteractionRequired(1);
        return confirmation;
    }
}
