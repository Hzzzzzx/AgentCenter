package com.agentcenter.bridge.application.runtime.translation;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.artifact.ArtifactCaptureService;
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
    private final ArtifactCaptureService artifactCaptureService;

    public RuntimeEventEnvelopeDispatcher(LegacyRuntimeEventBridge legacyBridge,
                                           AssistantMessageProjector projector,
                                           RuntimeEventService eventService,
                                           RuntimeOperationEventHandler operationHandler,
                                           PermissionConfirmationHandler permissionHandler,
                                           QuestionConfirmationHandler questionHandler,
                                           ArtifactCaptureService artifactCaptureService) {
        this.legacyBridge = legacyBridge;
        this.projector = projector;
        this.eventService = eventService;
        this.operationHandler = operationHandler;
        this.permissionHandler = permissionHandler;
        this.questionHandler = questionHandler;
        this.artifactCaptureService = artifactCaptureService;
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
                        extractSkillName(envelope),
                        extractPermissionContextJson(envelope)
                    );
                } catch (Exception e) {
                    log.warn("Failed to create permission confirmation: {}", e.getMessage());
                }
            }

            if (isPermissionRepliedTrace(envelope)) {
                try {
                    permissionHandler.handlePermissionReplied(
                            envelope.agentSessionId(),
                            envelope.runtimeSessionId(),
                            extractPermissionReplyId(envelope),
                            extractPermissionReply(envelope));
                } catch (Exception e) {
                    log.warn("Failed to sync permission reply: {}", e.getMessage());
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

            try {
                artifactCaptureService.captureFromRuntimeArtifact(envelope);
            } catch (Exception e) {
                log.warn("Failed to capture runtime artifact for event {}: {}",
                        envelope.type(), e.getMessage());
            }

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
                if (payload.has("permissionId")) return payload.get("permissionId").asText();
                if (payload.has("confirmationId")) return payload.get("confirmationId").asText();
                if (meta.has("permissionId")) return meta.get("permissionId").asText();
                if (meta.has("confirmationId")) return meta.get("confirmationId").asText();
            }
        } catch (Exception ignored) {}
        return envelope.messageId() != null ? envelope.messageId() : UUID.randomUUID().toString();
    }

    private boolean isPermissionRepliedTrace(RuntimeEventEnvelope envelope) {
        try {
            if (!RuntimeEventTypes.PROCESS_TRACE.equals(envelope.type())) return false;
            JsonNode payload = envelope.payload();
            return payload != null && "permission.replied".equals(payload.path("rawEventType").asText(""));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractPermissionReplyId(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode rawProperties = payload.path("rawProperties");
                if (payload.has("permissionId")) return payload.get("permissionId").asText();
                if (payload.has("toolCallId")) return payload.get("toolCallId").asText();
                if (rawProperties.has("requestID")) return rawProperties.get("requestID").asText();
                if (rawProperties.has("requestId")) return rawProperties.get("requestId").asText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractPermissionReply(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode rawProperties = payload.path("rawProperties");
                if (payload.has("reply")) return payload.get("reply").asText();
                if (rawProperties.has("reply")) return rawProperties.get("reply").asText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractTitle(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode meta = payload.path("meta");
                if (payload.has("title")) return payload.get("title").asText();
                if (meta.has("title")) return meta.get("title").asText();
            }
        } catch (Exception ignored) {}
        return "OpenCode permission request";
    }

    private String extractPermissionContextJson(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null) {
                JsonNode meta = payload.path("meta");
                if (meta != null && meta.isObject()) return meta.toString();
                if (payload.isObject()) return payload.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractSkillName(RuntimeEventEnvelope envelope) {
        try {
            JsonNode payload = envelope.payload();
            if (payload != null && payload.has("skillName")) {
                return payload.get("skillName").asText();
            }
            if (payload != null && payload.has("label")) {
                return payload.get("label").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
