package com.agentcenter.bridge.application.runtime.translation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AssistantMessageProjectorTest {

    private AgentMessageMapper mapper;
    private IdGenerator idGenerator;
    private AssistantMessageProjector projector;
    private static final String SESSION_ID = "agent_ses_1";

    @BeforeEach
    void setUp() {
        mapper = mock(AgentMessageMapper.class);
        idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn("msg_1");
        when(mapper.findBySessionId(SESSION_ID)).thenReturn(List.of());
        projector = new AssistantMessageProjector(mapper, idGenerator);
    }

    private RuntimeEventEnvelope deltaEnvelope(String text) {
        return deltaEnvelope("label", text);
    }

    private RuntimeEventEnvelope deltaEnvelope(String fieldName, String text) {
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("type", "assistant_delta");
        payload.put(fieldName, text);
        return new RuntimeEventEnvelope("runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            null, null, null, RuntimeType.OPENCODE, SESSION_ID, null, null, null, null,
            payload, OffsetDateTime.now());
    }

    private RuntimeEventEnvelope completedEnvelope() {
        return new RuntimeEventEnvelope("runtime-event", RuntimeEventTypes.CONVERSATION_COMPLETED,
            null, null, null, RuntimeType.OPENCODE, SESSION_ID, null, null, null, null,
            JsonNodeFactory.instance.objectNode(), OffsetDateTime.now());
    }

    @Test
    void aggregateDeltasAndFlushOnCompleted() {
        // Feed two deltas then a completed
        projector.onEnvelope(deltaEnvelope("Hello "));
        projector.onEnvelope(deltaEnvelope("World"));
        projector.onEnvelope(completedEnvelope());

        // Should have inserted one assistant message with "Hello World"
        verify(mapper).insert(argThat(msg ->
            SESSION_ID.equals(msg.getSessionId())
            && "ASSISTANT".equals(msg.getRole())
            && "Hello World".equals(msg.getContent())
            && "runtime-projector".equals(msg.getCreatedBy())
        ));
        verify(idGenerator).nextId();
    }

    @Test
    void aggregateDeltaFieldAndFlushOnCompleted() {
        projector.onEnvelope(deltaEnvelope("delta", "Agent output"));
        projector.onEnvelope(completedEnvelope());

        verify(mapper).insert(argThat(msg ->
            SESSION_ID.equals(msg.getSessionId())
            && "ASSISTANT".equals(msg.getRole())
            && "Agent output".equals(msg.getContent())
        ));
    }

    @Test
    void emptyBufferDoesNotInsert() {
        // Completed without any prior deltas → empty buffer
        projector.onEnvelope(completedEnvelope());

        verify(mapper, never()).insert(any());
    }

    @Test
    void duplicateLatestAssistantSkipsInsert() {
        // Pre-existing assistant message with same content
        AgentMessageEntity existing = new AgentMessageEntity();
        existing.setRole("ASSISTANT");
        existing.setContent("Hello World");
        when(mapper.findBySessionId(SESSION_ID)).thenReturn(List.of(existing));

        projector.onEnvelope(deltaEnvelope("Hello World"));
        projector.onEnvelope(completedEnvelope());

        // Should NOT insert — dedup detected
        verify(mapper, never()).insert(any());
    }

    @Test
    void insertFailureRestoresBuffer() {
        // First call: findBySessionId returns empty for seqNo check
        when(mapper.findBySessionId(SESSION_ID)).thenReturn(List.of());
        // insert throws
        doThrow(new RuntimeException("DB error")).when(mapper).insert(any(AgentMessageEntity.class));

        projector.onEnvelope(deltaEnvelope("Recovered text"));
        projector.onEnvelope(completedEnvelope());

        // Insert was attempted
        verify(mapper).insert(any());

        // Buffer should be restored — a second completed should retry
        // Reset mock: now findBySessionId returns empty again, insert succeeds
        reset(mapper);
        when(mapper.findBySessionId(SESSION_ID)).thenReturn(List.of());
        projector.onEnvelope(completedEnvelope());

        verify(mapper).insert(argThat(msg -> "Recovered text".equals(msg.getContent())));
    }

    @Test
    void cleanupSessionRemovesBuffer() {
        projector.onEnvelope(deltaEnvelope("Some text"));
        projector.cleanupSession(SESSION_ID);
        projector.onEnvelope(completedEnvelope());

        // Buffer was cleaned up — completed should not insert
        verify(mapper, never()).insert(any());
    }

    @Test
    void nullSessionIdIsIgnored() {
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("type", "assistant_delta");
        payload.put("label", "text");
        RuntimeEventEnvelope nullSession = new RuntimeEventEnvelope("runtime-event",
            RuntimeEventTypes.CONVERSATION_DELTA, null, null, null,
            RuntimeType.OPENCODE, null, null, null, null, null,
            payload, OffsetDateTime.now());

        // Should not throw or NPE
        assertDoesNotThrow(() -> projector.onEnvelope(nullSession));
    }
}
