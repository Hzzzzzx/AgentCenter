package com.agentcenter.bridge.application.runtime.translation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;

@Component
public class RuntimeEventEnvelopeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEventEnvelopeDispatcher.class);

    private final LegacyRuntimeEventBridge legacyBridge;
    private final AssistantMessageProjector projector;
    private final RuntimeEventService eventService;
    private final RuntimeOperationEventHandler operationHandler;

    public RuntimeEventEnvelopeDispatcher(LegacyRuntimeEventBridge legacyBridge,
                                           AssistantMessageProjector projector,
                                           RuntimeEventService eventService,
                                           RuntimeOperationEventHandler operationHandler) {
        this.legacyBridge = legacyBridge;
        this.projector = projector;
        this.eventService = eventService;
        this.operationHandler = operationHandler;
    }

    public void dispatch(List<RuntimeEventEnvelope> envelopes) {
        for (RuntimeEventEnvelope envelope : envelopes) {
            try {
                operationHandler.onEnvelope(envelope);
            } catch (Exception e) {
                log.warn("Operation handler failed for event {}: {}",
                        envelope.type(), e.getMessage());
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
}
