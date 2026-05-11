package com.agentcenter.bridge.application.runtime.translation;

import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.artifact.ArtifactCaptureService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;

class RuntimeEventEnvelopeDispatcherTest {

    private AssistantMessageProjector projector;
    private LegacyRuntimeEventBridge legacyBridge;
    private RuntimeEventService eventService;
    private RuntimeOperationEventHandler operationHandler;
    private PermissionConfirmationHandler permissionHandler;
    private QuestionConfirmationHandler questionHandler;
    private ArtifactCaptureService artifactCaptureService;
    private RuntimeEventEnvelopeDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        projector = mock(AssistantMessageProjector.class);
        legacyBridge = mock(LegacyRuntimeEventBridge.class);
        eventService = mock(RuntimeEventService.class);
        operationHandler = mock(RuntimeOperationEventHandler.class);
        permissionHandler = mock(PermissionConfirmationHandler.class);
        questionHandler = mock(QuestionConfirmationHandler.class);
        artifactCaptureService = mock(ArtifactCaptureService.class);
        dispatcher = new RuntimeEventEnvelopeDispatcher(legacyBridge, projector, eventService, operationHandler,
                permissionHandler, questionHandler, artifactCaptureService);
    }

    private RuntimeEventEnvelope envelope(String type) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return new RuntimeEventEnvelope(
                "runtime-event", type, null, null, null,
                RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
                mapper.readTree("{\"type\":\"test\",\"label\":\"value\"}"),
                OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeEventEnvelope envelope(String type, String payloadJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return new RuntimeEventEnvelope(
                "runtime-event", type, null, null, null,
                RuntimeType.OPENCODE, "agent_ses_1", "opencode_ses_1", null, null, null,
                mapper.readTree(payloadJson),
                OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeEventDto legacyDto(String sessionId) {
        return legacyDto(sessionId, "evt_1");
    }

    private RuntimeEventDto legacyDto(String sessionId, String eventId) {
        return new RuntimeEventDto(
            eventId, sessionId, null, null, null,
            RuntimeEventType.ASSISTANT_DELTA, RuntimeEventSource.OPENCODE,
            "{}", OffsetDateTime.now());
    }

    @Test
    void dispatchesToProjectorAndBridge() {
        RuntimeEventEnvelope env1 = envelope(RuntimeEventTypes.CONVERSATION_DELTA);
        RuntimeEventEnvelope env2 = envelope(RuntimeEventTypes.TOOL_STARTED);
        RuntimeEventDto dto1 = legacyDto("agent_ses_1", "evt_publish_1");
        RuntimeEventDto dto2 = legacyDto("agent_ses_1", "evt_publish_2");

        when(legacyBridge.toLegacyEvent(env1)).thenReturn(dto1);
        when(legacyBridge.toLegacyEvent(env2)).thenReturn(dto2);

        dispatcher.dispatch(List.of(env1, env2));

        verify(projector, times(2)).onEnvelope(any(RuntimeEventEnvelope.class));
        verify(artifactCaptureService, times(2)).captureFromRuntimeArtifact(any(RuntimeEventEnvelope.class));
        verify(legacyBridge, times(2)).toLegacyEvent(any(RuntimeEventEnvelope.class));
        verify(eventService, times(2)).publishEvent(any(RuntimeEventDto.class));
        verify(operationHandler, times(2)).onEnvelope(any(RuntimeEventEnvelope.class));
    }

    @Test
    void skipsNullLegacyEvent() {
        RuntimeEventEnvelope env1 = envelope(RuntimeEventTypes.CONVERSATION_DELTA);
        RuntimeEventEnvelope env2 = envelope(RuntimeEventTypes.CONVERSATION_COMPLETED);
        RuntimeEventDto dto1 = legacyDto("agent_ses_1");

        when(legacyBridge.toLegacyEvent(env1)).thenReturn(dto1);
        when(legacyBridge.toLegacyEvent(env2)).thenReturn(null);

        dispatcher.dispatch(List.of(env1, env2));

        verify(projector, times(2)).onEnvelope(any(RuntimeEventEnvelope.class));
        verify(eventService, times(1)).publishEvent(dto1);
    }

    @Test
    void continuesOnPublishError() {
        RuntimeEventEnvelope env1 = envelope(RuntimeEventTypes.CONVERSATION_DELTA);
        RuntimeEventEnvelope env2 = envelope(RuntimeEventTypes.TOOL_STARTED);
        RuntimeEventDto dto1 = legacyDto("agent_ses_1", "evt_1");
        RuntimeEventDto dto2 = legacyDto("agent_ses_1", "evt_2");

        when(legacyBridge.toLegacyEvent(env1)).thenReturn(dto1);
        when(legacyBridge.toLegacyEvent(env2)).thenReturn(dto2);
        doThrow(new RuntimeException("publish failed")).when(eventService).publishEvent(dto1);

        dispatcher.dispatch(List.of(env1, env2));

        verify(projector, times(2)).onEnvelope(any(RuntimeEventEnvelope.class));
        verify(eventService).publishEvent(dto1);
        verify(eventService).publishEvent(dto2);
    }

    @Test
    void continuesOnOperationHandlerError() {
        RuntimeEventEnvelope env1 = envelope(RuntimeEventTypes.CONVERSATION_DELTA);
        RuntimeEventDto dto1 = legacyDto("agent_ses_1");

        when(legacyBridge.toLegacyEvent(env1)).thenReturn(dto1);
        doThrow(new RuntimeException("operation handler failed")).when(operationHandler).onEnvelope(env1);

        dispatcher.dispatch(List.of(env1));

        verify(operationHandler).onEnvelope(env1);
        verify(projector).onEnvelope(env1);
        verify(eventService).publishEvent(dto1);
    }

    @Test
    void questionRequestedCreatesConfirmation() {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.QUESTION_REQUESTED);

        dispatcher.dispatch(List.of(env));

        verify(questionHandler).createQuestionConfirmation(env);
        verify(projector).onEnvelope(env);
    }

    @Test
    void permissionRequestedPassesTargetContextToConfirmation() {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.PERMISSION_REQUESTED, """
            {
              "type": "permission_required",
              "label": "Bash",
              "permissionId": "perm_1",
              "confirmationId": "perm_opencode_ses_1_perm_1",
              "title": "Allow file write?",
              "permission": "file_write",
              "filePath": "C:\\\\Users\\\\alice\\\\workspace\\\\demo\\\\SKILL.md"
            }
            """);

        dispatcher.dispatch(List.of(env));

        verify(permissionHandler).createPermissionConfirmation(
                eq("agent_ses_1"),
                eq("opencode_ses_1"),
                eq("perm_1"),
                eq("Allow file write?"),
                eq("Bash"),
                contains("C:\\\\Users\\\\alice\\\\workspace\\\\demo\\\\SKILL.md"));
    }

    @Test
    void emptyEnvelopeListDoesNothing() {
        dispatcher.dispatch(List.of());
        verifyNoInteractions(projector, legacyBridge, eventService);
        verifyNoInteractions(operationHandler);
        verifyNoInteractions(permissionHandler, questionHandler);
    }
}
