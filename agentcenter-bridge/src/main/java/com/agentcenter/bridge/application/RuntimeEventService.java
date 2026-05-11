package com.agentcenter.bridge.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.event.SseEmitterRegistry;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RuntimeEventService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEventService.class);
    private static final int SQLITE_BUSY_MAX_RETRIES = 5;
    private static final long SQLITE_BUSY_RETRY_SLEEP_MS = 80L;
    private static final int LOG_FIELD_LIMIT = 180;
    private static final ObjectMapper TRACE_OBJECT_MAPPER = new ObjectMapper();

    private final RuntimeEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final SseEmitterRegistry emitterRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final Object eventWriteLock = new Object();

    @Value("${agentcenter.trace.conversation-log-enabled:false}")
    private boolean conversationLogEnabled;

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
        if (event == null) {
            return;
        }
        if (isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishImmediately(event);
                }
            });
            return;
        }
        publishImmediately(event);
    }

    public void publishCommittedEvent(RuntimeEventDto event) {
        if (event == null) {
            return;
        }
        publishImmediately(event);
    }

    private boolean isActualTransactionActive() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void publishImmediately(RuntimeEventDto event) {
        persistAndBroadcast(event);
    }

    private void persistAndBroadcast(RuntimeEventDto event) {
        RuntimeEventEntity entity;
        synchronized (eventWriteLock) {
            entity = persistWithRetry(event);
        }
        if (event.sessionId() == null) {
            return;
        }
        RuntimeEventDto withId = new RuntimeEventDto(
                entity.getId(),
                event.sessionId(),
                event.workItemId(),
                event.workflowInstanceId(),
                event.workflowNodeInstanceId(),
                event.eventType(),
                event.eventSource(),
                event.payloadJson(),
                entity.getSeqNo(),
                parseDateTime(entity.getCreatedAt())
        );
        logConversationEvent(withId);
        emitterRegistry.sendToSession(event.sessionId(), withId);
        webSocketSessionRegistry.sendToSession(event.sessionId(), Map.of(
                "type", "runtime.event",
                "payload", withId
        ));
    }

    private RuntimeEventEntity persistWithRetry(RuntimeEventDto event) {
        String eventId = event.id() != null ? event.id() : idGenerator.nextId();
        String createdAt = formatDateTime(event.createdAt() != null ? event.createdAt() : OffsetDateTime.now());
        RuntimeEventEntity entity = null;
        for (int attempt = 1; ; attempt++) {
            try {
                entity = buildEntity(event, eventId, createdAt);
                eventMapper.insert(entity);
                return entity;
            } catch (Exception e) {
                if (entity == null) {
                    entity = buildEntityWithoutSeqNo(event, eventId, createdAt);
                }
                if (!isSqliteBusy(e) || attempt >= SQLITE_BUSY_MAX_RETRIES) {
                    log.warn("Runtime event persistence failed; streaming event without DB record: {}",
                            errorMessage(e));
                    return entity;
                }
                sleepBeforeRetry(attempt);
            }
        }
    }

    private RuntimeEventEntity buildEntity(RuntimeEventDto event, String eventId, String createdAt) {
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.setId(eventId);
        entity.setSessionId(event.sessionId());
        entity.setWorkItemId(event.workItemId());
        entity.setWorkflowInstanceId(event.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(event.workflowNodeInstanceId());
        entity.setEventType(event.eventType().name());
        entity.setEventSource(event.eventSource().name());
        entity.setPayloadJson(event.payloadJson());
        entity.setSeqNo(event.seqNo() != null ? event.seqNo() : nextSeqNo(event.sessionId()));
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private RuntimeEventEntity buildEntityWithoutSeqNo(RuntimeEventDto event, String eventId, String createdAt) {
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.setId(eventId);
        entity.setSessionId(event.sessionId());
        entity.setWorkItemId(event.workItemId());
        entity.setWorkflowInstanceId(event.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(event.workflowNodeInstanceId());
        entity.setEventType(event.eventType().name());
        entity.setEventSource(event.eventSource().name());
        entity.setPayloadJson(event.payloadJson());
        entity.setSeqNo(event.seqNo());
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private boolean isSqliteBusy(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("SQLITE_BUSY")
                    || message.contains("database is locked")
                    || message.contains("database table is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = SQLITE_BUSY_RETRY_SLEEP_MS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }

    private void logConversationEvent(RuntimeEventDto event) {
        if (!conversationLogEnabled && !log.isDebugEnabled()) {
            return;
        }
        String payload = event.payloadJson();
        String summary = firstPayloadField(payload, "summary", "errorMessage", "message", "title");
        String logLine = "conversation.event id=%s session=%s seq=%s type=%s source=%s workItem=%s workflow=%s node=%s kind=%s status=%s errorCode=%s reason=%s recoverable=%s summary=%s"
                .formatted(
                        event.id(),
                        event.sessionId(),
                        event.seqNo(),
                        event.eventType(),
                        event.eventSource(),
                        event.workItemId(),
                        event.workflowInstanceId(),
                        event.workflowNodeInstanceId(),
                        firstPayloadField(payload, "kind", "type"),
                        firstPayloadField(payload, "status", "label"),
                        firstPayloadField(payload, "errorCode"),
                        firstPayloadField(payload, "reason"),
                        firstPayloadField(payload, "recoverable"),
                        summary
                );
        if (conversationLogEnabled) {
            if (RuntimeEventType.ERROR.equals(event.eventType())) {
                log.warn(logLine);
            } else {
                log.info(logLine);
            }
            return;
        }
        log.debug(logLine);
    }

    private String firstPayloadField(String payloadJson, String... fields) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return "-";
        }
        try {
            JsonNode root = TRACE_OBJECT_MAPPER.readTree(payloadJson);
            for (String field : fields) {
                JsonNode value = root.path(field);
                if (!value.isMissingNode() && !value.isNull()) {
                    String text = value.isValueNode() ? value.asText() : value.toString();
                    if (!text.isBlank()) {
                        return clipForLog(text);
                    }
                }
            }
        } catch (Exception ignored) {
            return "[unparseable]";
        }
        return "-";
    }

    private String clipForLog(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= LOG_FIELD_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, LOG_FIELD_LIMIT) + "...";
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
                e.getSeqNo(),
                parseDateTime(e.getCreatedAt())
        );
    }

    private Integer nextSeqNo(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return eventMapper.nextSeqNo(sessionId);
    }

    private String formatDateTime(OffsetDateTime value) {
        return value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
