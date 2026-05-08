package com.agentcenter.bridge.application.runtime.translation;

import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.agentcenter.bridge.application.runtime.RuntimeOperationService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class RuntimeOperationEventHandlerTest {

    private RuntimeOperationService operationService;
    private RuntimeOperationEventHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        operationService = mock(RuntimeOperationService.class);
        handler = new RuntimeOperationEventHandler(operationService);
        objectMapper = new ObjectMapper();
    }

    private RuntimeOperationEntity createdOperation() {
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId("op_1");
        entity.setStatus(RuntimeOperationStatus.CREATED.name());
        return entity;
    }

    private RuntimeOperationEntity terminalOperation() {
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId("op_1");
        entity.setStatus(RuntimeOperationStatus.SUCCEEDED.name());
        return entity;
    }

    private RuntimeEventEnvelope envelope(String type, String operationId, String payloadJson) {
        try {
            return new RuntimeEventEnvelope(
                    "runtime-event", type, null, null, operationId,
                    RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
                    payloadJson != null ? objectMapper.readTree(payloadJson) : null,
                    OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void skipsEnvelopeWithNullOperationId() {
        RuntimeEventEnvelope envelope = envelope("skill.install.completed", null, "{}");

        handler.onEnvelope(envelope);

        verifyNoInteractions(operationService);
    }

    @Test
    void skipsEnvelopeWithBlankOperationId() {
        RuntimeEventEnvelope envelope = envelope("skill.install.completed", "   ", "{}");

        handler.onEnvelope(envelope);

        verifyNoInteractions(operationService);
    }

    @Test
    void skipsEnvelopeForUnknownOperation() {
        RuntimeEventEnvelope envelope = envelope("skill.install.completed", "op_unknown", "{}");
        when(operationService.findById("op_unknown")).thenReturn(null);

        handler.onEnvelope(envelope);

        verify(operationService).findById("op_unknown");
        verifyNoMoreInteractions(operationService);
    }

    @Test
    void skipsEnvelopeForTerminalOperation() {
        RuntimeEventEnvelope envelope = envelope("skill.install.completed", "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(terminalOperation());

        handler.onEnvelope(envelope);

        verify(operationService).findById("op_1");
        verifyNoMoreInteractions(operationService);
    }

    @Test
    void transitionsToSucceededOnCompletedEvent() {
        RuntimeEventEnvelope envelope = envelope("skill.install.completed", "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transition("op_1", RuntimeOperationStatus.SUCCEEDED);
    }

    @Test
    void transitionsToFailedOnFailedEvent() {
        RuntimeEventEnvelope envelope = envelope("skill.install.failed", "op_1",
                "{\"error\":\"something went wrong\"}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "something went wrong");
    }

    @Test
    void transitionsToFailedOnMcpRefreshFailed() {
        RuntimeEventEnvelope envelope = envelope("mcp.refresh.failed", "op_1",
                "{\"error\":\"timeout\"}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "timeout");
    }

    @Test
    void transitionsToSucceededOnMcpConfigUpdated() {
        RuntimeEventEnvelope envelope = envelope("mcp.config.updated", "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transition("op_1", RuntimeOperationStatus.SUCCEEDED);
    }

    @Test
    void transitionsToInProgressOnStartedEvent() {
        RuntimeEventEnvelope envelope = envelope("skill.run.started", "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transition("op_1", RuntimeOperationStatus.IN_PROGRESS);
    }

    @Test
    void transitionsToFailedOnRuntimeError() {
        RuntimeEventEnvelope envelope = envelope("runtime.error", "op_1",
                "{\"message\":\"runtime crashed\"}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "runtime crashed");
    }

    @Test
    void ignoresUnmappedEventType() {
        RuntimeEventEnvelope envelope = envelope("tool.started", "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).findById("op_1");
        verifyNoMoreInteractions(operationService);
    }

    @Test
    void extractsErrorMessageFromNestedErrorObject() {
        RuntimeEventEnvelope envelope = envelope("skill.run.failed", "op_1",
                "{\"error\":{\"message\":\"nested error detail\"}}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "nested error detail");
    }

    @Test
    void extractsErrorMessageFromPayloadMessage() {
        RuntimeEventEnvelope envelope = envelope("skill.delete.failed", "op_1",
                "{\"message\":\"top-level message\"}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "top-level message");
    }

    @Test
    void extractsUnknownErrorWhenNoPayload() {
        RuntimeEventEnvelope envelope = envelope("skill.run.failed", "op_1", null);
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "Unknown error");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            RuntimeEventTypes.SKILL_INSTALL_COMPLETED,
            RuntimeEventTypes.SKILL_DELETE_COMPLETED,
            RuntimeEventTypes.SKILL_RUN_COMPLETED,
            RuntimeEventTypes.MCP_REFRESH_COMPLETED
    })
    void completedEventsTransitionToSucceeded(String eventType) {
        RuntimeEventEnvelope envelope = envelope(eventType, "op_1", "{}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transition("op_1", RuntimeOperationStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            RuntimeEventTypes.SKILL_INSTALL_FAILED,
            RuntimeEventTypes.SKILL_DELETE_FAILED,
            RuntimeEventTypes.SKILL_RUN_FAILED,
            RuntimeEventTypes.MCP_REFRESH_FAILED
    })
    void failedEventsTransitionToFailed(String eventType) {
        RuntimeEventEnvelope envelope = envelope(eventType, "op_1",
                "{\"error\":\"fail reason\"}");
        when(operationService.findById("op_1")).thenReturn(createdOperation());

        handler.onEnvelope(envelope);

        verify(operationService).transitionToFailed("op_1", "RUNTIME_ERROR", "fail reason");
    }
}
