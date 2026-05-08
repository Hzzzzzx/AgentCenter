package com.agentcenter.bridge.application.runtime.translation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;

@Component
public class AssistantMessageProjector {

    private static final Logger log = LoggerFactory.getLogger(AssistantMessageProjector.class);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentMessageMapper agentMessageMapper;
    private final IdGenerator idGenerator;
    private final ConcurrentHashMap<String, StringBuilder> buffers = new ConcurrentHashMap<>();

    public AssistantMessageProjector(AgentMessageMapper agentMessageMapper, IdGenerator idGenerator) {
        this.agentMessageMapper = agentMessageMapper;
        this.idGenerator = idGenerator;
    }

    public void onEnvelope(RuntimeEventEnvelope envelope) {
        String sessionId = envelope.agentSessionId();
        if (sessionId == null) return;

        switch (envelope.type()) {
            case RuntimeEventTypes.CONVERSATION_DELTA -> {
                String text = envelope.payload() != null ? envelope.payload().path("label").asText("") : "";
                if (!text.isEmpty()) {
                    StringBuilder buffer = buffers.computeIfAbsent(sessionId, k -> new StringBuilder());
                    synchronized (buffer) {
                        buffer.append(text);
                    }
                }
            }
            case RuntimeEventTypes.CONVERSATION_COMPLETED -> flushBuffer(sessionId);
        }
    }

    public void cleanupSession(String agentSessionId) {
        buffers.remove(agentSessionId);
    }

    private void flushBuffer(String agentSessionId) {
        StringBuilder buffer = buffers.computeIfAbsent(agentSessionId, k -> new StringBuilder());
        String content;
        synchronized (buffer) {
            content = buffer.toString().trim();
            buffer.setLength(0);
        }
        if (content.isBlank()) return;
        if (isDuplicateLatestAssistant(agentSessionId, content)) return;

        try {
            AgentMessageEntity message = new AgentMessageEntity();
            message.setId(idGenerator.nextId());
            message.setSessionId(agentSessionId);
            message.setRole(MessageRole.ASSISTANT.name());
            message.setContent(content);
            message.setContentFormat(ContentFormat.MARKDOWN.name());
            message.setStatus(MessageStatus.COMPLETED.name());
            message.setSeqNo(nextMessageSeqNo(agentSessionId));
            message.setCreatedBy("runtime-projector");
            message.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            agentMessageMapper.insert(message);
        } catch (Exception e) {
            log.warn("Failed to persist streamed assistant message for session {}: {}", agentSessionId, e.getMessage());
            synchronized (buffer) {
                buffer.insert(0, content);
            }
        }
    }

    private boolean isDuplicateLatestAssistant(String agentSessionId, String content) {
        return agentMessageMapper.findBySessionId(agentSessionId).stream()
                .filter(m -> MessageRole.ASSISTANT.name().equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(m -> content.equals(m.getContent()))
                .orElse(false);
    }

    private int nextMessageSeqNo(String agentSessionId) {
        return agentMessageMapper.findBySessionId(agentSessionId).stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;
    }
}
