package com.agentcenter.bridge.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.event.SseEmitterRegistry;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;

@Service
public class RuntimeEventService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEventService.class);

    private final RuntimeEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final SseEmitterRegistry emitterRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public RuntimeEventService(RuntimeEventMapper eventMapper,
                               IdGenerator idGenerator,
                               SseEmitterRegistry emitterRegistry,
                               WebSocketSessionRegistry webSocketSessionRegistry) {
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
        this.emitterRegistry = emitterRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public List<RuntimeEventDto> getEventsBySession(String sessionId) {
        return eventMapper.findBySessionId(sessionId).stream()
                .map(this::toDto)
                .toList();
    }

    public void publishEvent(RuntimeEventDto event) {
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.setId(event.id() != null ? event.id() : idGenerator.nextId());
        entity.setSessionId(event.sessionId());
        entity.setWorkItemId(event.workItemId());
        entity.setWorkflowInstanceId(event.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(event.workflowNodeInstanceId());
        entity.setEventType(event.eventType().name());
        entity.setEventSource(event.eventSource().name());
        entity.setPayloadJson(event.payloadJson());
        boolean persisted = true;
        try {
            eventMapper.insert(entity);
        } catch (Exception e) {
            persisted = false;
            log.warn("Runtime event persistence failed; streaming event without DB record: {}", e.getMessage());
        }

        if (event.sessionId() != null) {
            RuntimeEventDto withId = new RuntimeEventDto(
                    entity.getId(),
                    event.sessionId(),
                    event.workItemId(),
                    event.workflowInstanceId(),
                    event.workflowNodeInstanceId(),
                    event.eventType(),
                    event.eventSource(),
                    event.payloadJson(),
                    persisted ? parseDateTime(entity.getCreatedAt()) : null
            );
            emitterRegistry.sendToSession(event.sessionId(), withId);
            webSocketSessionRegistry.sendToSession(event.sessionId(), java.util.Map.of(
                    "type", "runtime.event",
                    "payload", withId
            ));
        }
    }

    private RuntimeEventDto toDto(RuntimeEventEntity e) {
        return new RuntimeEventDto(
                e.getId(),
                e.getSessionId(),
                e.getWorkItemId(),
                e.getWorkflowInstanceId(),
                e.getWorkflowNodeInstanceId(),
                RuntimeEventType.valueOf(e.getEventType()),
                RuntimeEventSource.valueOf(e.getEventSource()),
                e.getPayloadJson(),
                parseDateTime(e.getCreatedAt())
        );
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return OffsetDateTime.parse(value.replace(" ", "T") + "+00:00",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
}
