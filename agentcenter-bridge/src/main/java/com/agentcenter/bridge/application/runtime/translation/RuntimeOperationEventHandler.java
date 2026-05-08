package com.agentcenter.bridge.application.runtime.translation;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.RuntimeOperationService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class RuntimeOperationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RuntimeOperationEventHandler.class);

    private static final Set<String> COMPLETED_TYPES = Set.of(
            RuntimeEventTypes.SKILL_INSTALL_COMPLETED,
            RuntimeEventTypes.SKILL_DELETE_COMPLETED,
            RuntimeEventTypes.SKILL_RUN_COMPLETED,
            RuntimeEventTypes.MCP_REFRESH_COMPLETED,
            RuntimeEventTypes.MCP_CONFIG_UPDATED);

    private static final Set<String> FAILED_TYPES = Set.of(
            RuntimeEventTypes.SKILL_INSTALL_FAILED,
            RuntimeEventTypes.SKILL_DELETE_FAILED,
            RuntimeEventTypes.SKILL_RUN_FAILED,
            RuntimeEventTypes.MCP_REFRESH_FAILED);

    private static final Set<String> STARTED_TYPES = Set.of(
            RuntimeEventTypes.SKILL_RUN_STARTED);

    private final RuntimeOperationService operationService;

    public RuntimeOperationEventHandler(RuntimeOperationService operationService) {
        this.operationService = operationService;
    }

    public void onEnvelope(RuntimeEventEnvelope envelope) {
        String operationId = envelope.operationId();
        if (operationId == null || operationId.isBlank()) {
            return;
        }

        RuntimeOperationEntity operation = operationService.findById(operationId);
        if (operation == null) {
            log.warn("Event {} references unknown operation {}", envelope.type(), operationId);
            return;
        }

        RuntimeOperationStatus current = RuntimeOperationStatus.valueOf(operation.getStatus());
        if (current.isTerminal()) {
            log.debug("Operation {} already terminal ({}) – ignoring {}",
                    operationId, current, envelope.type());
            return;
        }

        handleEventType(envelope, operation);
    }

    private void handleEventType(RuntimeEventEnvelope envelope, RuntimeOperationEntity operation) {
        String type = envelope.type();
        String operationId = operation.getId();

        if (COMPLETED_TYPES.contains(type)) {
            operationService.transition(operationId, RuntimeOperationStatus.SUCCEEDED);
        } else if (FAILED_TYPES.contains(type)) {
            String errorMessage = extractErrorMessage(envelope);
            operationService.transitionToFailed(operationId, "RUNTIME_ERROR", errorMessage);
        } else if (STARTED_TYPES.contains(type)) {
            operationService.transition(operationId, RuntimeOperationStatus.IN_PROGRESS);
        } else if (RuntimeEventTypes.RUNTIME_ERROR.equals(type)) {
            String errorMessage = extractErrorMessage(envelope);
            operationService.transitionToFailed(operationId, "RUNTIME_ERROR", errorMessage);
        }
    }

    private String extractErrorMessage(RuntimeEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        if (payload != null && payload.has("error")) {
            JsonNode error = payload.get("error");
            if (error.isTextual()) {
                return error.asText();
            }
            if (error.isObject() && error.has("message")) {
                return error.get("message").asText();
            }
        }
        if (payload != null && payload.has("message")) {
            return payload.get("message").asText();
        }
        return "Unknown error";
    }
}
