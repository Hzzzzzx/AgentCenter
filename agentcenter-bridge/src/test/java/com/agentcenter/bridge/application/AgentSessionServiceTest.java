package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
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

    private AgentSessionService serviceWith(ConfirmationMapper confirmationMapper) {
        return new AgentSessionService(
                mock(AgentSessionMapper.class),
                mock(AgentMessageMapper.class),
                confirmationMapper,
                mock(RuntimeEventMapper.class),
                mock(IdGenerator.class),
                mock(RuntimeGateway.class),
                mock(WebSocketSessionRegistry.class),
                mock(RuntimeEventService.class),
                mock(WorkflowCommandService.class),
                mock(WorkflowMapper.class),
                mock(SkillRegistryService.class),
                mock(RuntimeResourceService.class),
                mock(McpRegistryService.class),
                mock(WorkItemMapper.class),
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
