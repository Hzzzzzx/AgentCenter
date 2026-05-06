package com.agentcenter.bridge.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.AgentMessageDto;
import com.agentcenter.bridge.api.dto.AgentSessionDto;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.api.dto.SendMessageRequest;
import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.domain.session.SessionStatus;
import com.agentcenter.bridge.domain.session.SessionType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;

import jakarta.annotation.PreDestroy;

@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);
    private static final AtomicInteger MESSAGE_THREAD_COUNTER = new AtomicInteger(1);

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final RuntimeEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final AgentRuntimeAdapter agentRuntimeAdapter;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final ExecutorService messageExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agentcenter-message-" + MESSAGE_THREAD_COUNTER.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public AgentSessionService(AgentSessionMapper sessionMapper,
                               AgentMessageMapper messageMapper,
                               RuntimeEventMapper eventMapper,
                               IdGenerator idGenerator,
                               AgentRuntimeAdapter agentRuntimeAdapter,
                               WebSocketSessionRegistry webSocketSessionRegistry) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
        this.agentRuntimeAdapter = agentRuntimeAdapter;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @PreDestroy
    public void shutdown() {
        messageExecutor.shutdownNow();
    }

    public List<AgentSessionDto> listSessions() {
        return sessionMapper.findAll().stream()
                .map(this::toSessionDto)
                .toList();
    }

    public AgentSessionDto getSession(String id) {
        AgentSessionEntity entity = sessionMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id);
        }
        return toSessionDto(entity);
    }

    public AgentSessionDto createSession(SessionType sessionType, String title,
                                          String workItemId, String workflowInstanceId,
                                          RuntimeType runtimeType) {
        // Runtime session is lazily created on first sendMessage when we have the DB session ID.
        return createSession(sessionType, title, workItemId, workflowInstanceId, runtimeType, null);
    }

    public AgentSessionDto createSession(SessionType sessionType, String title,
                                          String workItemId, String workflowInstanceId,
                                          RuntimeType runtimeType, String runtimeSessionId) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setId(idGenerator.nextId());
        entity.setSessionType(sessionType.name());
        entity.setTitle(title);
        entity.setWorkItemId(workItemId);
        entity.setWorkflowInstanceId(workflowInstanceId);
        entity.setRuntimeType(runtimeType.name());
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setStatus(SessionStatus.ACTIVE.name());
        entity.setCreatedBy("system");
        sessionMapper.insert(entity);
        return toSessionDto(entity);
    }

    public AgentSessionDto bindRuntimeSession(String sessionId, String runtimeSessionId, RuntimeType runtimeType) {
        AgentSessionEntity entity = sessionMapper.findById(sessionId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setRuntimeType(runtimeType.name());
        sessionMapper.update(entity);
        return toSessionDto(entity);
    }

    public List<AgentMessageDto> getMessages(String sessionId) {
        return messageMapper.findBySessionId(sessionId).stream()
                .map(this::toMessageDto)
                .toList();
    }

    public AgentMessageDto sendMessage(String sessionId, SendMessageRequest request) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }

        List<AgentMessageEntity> existing = messageMapper.findBySessionId(sessionId);
        int nextSeqNo = existing.stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;

        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setId(idGenerator.nextId());
        entity.setSessionId(sessionId);
        entity.setRole(MessageRole.USER.name());
        entity.setContent(request.content());
        entity.setContentFormat(request.contentFormat().name());
        entity.setStatus(MessageStatus.COMPLETED.name());
        entity.setSeqNo(nextSeqNo);
        entity.setCreatedBy("system");
        messageMapper.insert(entity);

        messageExecutor.submit(() -> dispatchToRuntime(sessionId, request.content()));
        return toMessageDto(entity);
    }

    private void dispatchToRuntime(String sessionId, String content) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            return;
        }

        String effectiveRuntimeSessionId = session.getRuntimeSessionId();
        boolean runtimeOk = true;
        String runtimeError = null;

        if (effectiveRuntimeSessionId == null || effectiveRuntimeSessionId.isBlank()) {
            try {
                effectiveRuntimeSessionId = agentRuntimeAdapter.createSession(session.getWorkItemId(), sessionId);
                session.setRuntimeSessionId(effectiveRuntimeSessionId);
                sessionMapper.update(session);
            } catch (Exception e) {
                runtimeOk = false;
                runtimeError = "Failed to create runtime session: " + e.getMessage();
            }
        }

        if (runtimeOk && effectiveRuntimeSessionId != null) {
            try {
                agentRuntimeAdapter.sendMessage(effectiveRuntimeSessionId, content);
            } catch (Exception e) {
                runtimeOk = false;
                runtimeError = "Runtime sendMessage failed: " + e.getMessage();
            }
        }

        recordSendMessageEvent(sessionId, session, runtimeOk, runtimeError, content);

        if (!runtimeOk && runtimeError != null) {
            insertRuntimeErrorMessage(sessionId, effectiveRuntimeSessionId, runtimeError);
        }

        broadcastMessages(sessionId);
    }

    private void recordSendMessageEvent(String sessionId,
                                        AgentSessionEntity session,
                                        boolean runtimeOk,
                                        String runtimeError,
                                        String content) {
        RuntimeEventEntity eventEntity = new RuntimeEventEntity();
        eventEntity.setId(idGenerator.nextId());
        eventEntity.setSessionId(sessionId);
        eventEntity.setEventType(RuntimeEventType.STATUS.name());
        eventEntity.setEventSource(RuntimeEventSource.BRIDGE.name());
        RuntimeType runtimeType = RuntimeType.valueOf(session.getRuntimeType());
        eventEntity.setPayloadJson("""
                {"action":"sendMessage","runtimeType":"%s","runtimeOk":%s,"runtimeError":"%s","content":"%s"}
                """.formatted(runtimeType.name(), runtimeOk, jsonEscape(runtimeError), jsonEscape(content)).trim());
        try {
            eventMapper.insert(eventEntity);
        } catch (Exception e) {
            log.debug("Failed to record sendMessage runtime event for {}", sessionId, e);
        }
    }

    private void insertRuntimeErrorMessage(String sessionId, String runtimeSessionId, String runtimeError) {
        AgentMessageEntity errorMsg = new AgentMessageEntity();
        errorMsg.setId(idGenerator.nextId());
        errorMsg.setSessionId(sessionId);
        errorMsg.setRole(MessageRole.ASSISTANT.name());
        errorMsg.setContent("Runtime 调用失败：" + runtimeError);
        errorMsg.setContentFormat(ContentFormat.TEXT.name());
        errorMsg.setStatus(MessageStatus.COMPLETED.name());
        errorMsg.setSeqNo(nextMessageSeqNo(sessionId));
        errorMsg.setRuntimeMessageId(runtimeSessionId);
        errorMsg.setCreatedBy("runtime");
        messageMapper.insert(errorMsg);
    }

    private int nextMessageSeqNo(String sessionId) {
        return messageMapper.findBySessionId(sessionId).stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;
    }

    private void broadcastMessages(String sessionId) {
        try {
            webSocketSessionRegistry.sendToSession(sessionId, Map.of(
                    "type", "session.messages",
                    "payload", Map.of(
                            "sessionId", sessionId,
                            "messages", getMessages(sessionId)
                    )
            ));
        } catch (Exception e) {
            log.debug("Failed to broadcast session message snapshot for {}", sessionId, e);
        }
    }

    public void recordEvent(RuntimeEventDto event) {
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.setId(event.id() != null ? event.id() : idGenerator.nextId());
        entity.setSessionId(event.sessionId());
        entity.setWorkItemId(event.workItemId());
        entity.setWorkflowInstanceId(event.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(event.workflowNodeInstanceId());
        entity.setEventType(event.eventType().name());
        entity.setEventSource(event.eventSource().name());
        entity.setPayloadJson(event.payloadJson());
        eventMapper.insert(entity);
    }

    private AgentSessionDto toSessionDto(AgentSessionEntity e) {
        return new AgentSessionDto(
                e.getId(),
                SessionType.valueOf(e.getSessionType()),
                e.getTitle(),
                e.getWorkItemId(),
                e.getWorkflowInstanceId(),
                RuntimeType.valueOf(e.getRuntimeType()),
                SessionStatus.valueOf(e.getStatus()),
                parseDateTime(e.getCreatedAt())
        );
    }

    private AgentMessageDto toMessageDto(AgentMessageEntity e) {
        return new AgentMessageDto(
                e.getId(),
                e.getSessionId(),
                MessageRole.valueOf(e.getRole()),
                e.getContent(),
                ContentFormat.valueOf(e.getContentFormat()),
                MessageStatus.valueOf(e.getStatus()),
                e.getSeqNo() != null ? e.getSeqNo() : 0,
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
            // SQLite may store without timezone — try as local datetime
            return OffsetDateTime.parse(value.replace(" ", "T") + "+00:00",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
