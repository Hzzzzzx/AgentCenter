package com.agentcenter.bridge.application.runtime.translation;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class RuntimeEventEnvelopeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEventEnvelopeDispatcher.class);

    private final LegacyRuntimeEventBridge legacyBridge;
    private final AssistantMessageProjector projector;
    private final RuntimeEventService eventService;
    private final RuntimeOperationEventHandler operationHandler;
    private final PermissionConfirmationHandler permissionHandler;
    private final QuestionConfirmationHandler questionHandler;

    public RuntimeEventEnvelopeDispatcher(LegacyRuntimeEventBridge legacyBridge,
                                           AssistantMessageProjector projector,
                                           RuntimeEventService eventService,
                                           RuntimeOperationEventHandler operationHandler,
                                           PermissionConfirmationHandler permissionHandler,
                                           QuestionConfirmationHandler questionHandler) {
        this.legacyBridge = legacyBridge;
        this.projector = projector;
        this.eventService = eventService;
        this.operationHandler = operationHandler;
        this.permissionHandler = permissionHandler;
        this.questionHandler = questionHandler;
    }

    public void dispatch(List<RuntimeEventEnvelope> envelopes) {
        for (RuntimeEventEnvelope envelope : envelopes) {
            try {
                operationHandler.onEnvelope(envelope);
            } catch (Exception e) {
                log.warn("Operation handler failed for event {}: {}",
                        envelope.type(), e.getMessage());
            }

            if (RuntimeEventTypes.PERMISSION_REQUESTED.equals(envelope.type())) {
                try {
                    permissionHandler.createPermissionConfirmation(
                        envelope.agentSessionId(),
                        envelope.runtimeSessionId(),
                        extractPermissionId(envelope),
                        extractTitle(envelope),
                        extractSkillName(envelope)
                    );
                } catch (Exception e) {
                    log.warn("Failed to create permission confirmation: {}", e.getMessage());
                }
            }

            if (RuntimeEventTypes.QUESTION_REQUESTED.equals(envelope.type())) {
                try {
                    questionHandler.createQuestionConfirmation(envelope);
                } catch (Exception e) {
                    log.warn("Failed to create question confirmation: {}", e.getMessage());
                }
            }

            projector.onEnvelope(envelope);

            RuntimeEventDto legacyEvent = legacyBridge.toLegacyEvent(envelope);
            if (legacyEvent != null) {
                try {
                    eventService.publishEvent(legacyEvent);
                } catch (Exception e) {
                    log.warn("Failed to publish legacy event for session {}: {}",
                            envelope.agentSessionId(), e.getMessage());
                }
            }
        }
    }

    private String extractPermissionId(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode meta = payload.path("meta");
                if (meta.has("permissionId")) return meta.get("permissionId").asText();
                if (meta.has("confirmationId")) return meta.get("confirmationId").asText();
            }
        } catch (Exception ignored) {}
        return envelope.messageId() != null ? envelope.messageId() : UUID.randomUUID().toString();
    }

    private String extractTitle(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode meta = payload.path("meta");
                if (meta.has("title")) return meta.get("title").asText();
            }
        } catch (Exception ignored) {}
        return "OpenCode permission request";
    }

    private String extractSkillName(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null && payload.has("skillName")) {
                return payload.get("skillName").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
