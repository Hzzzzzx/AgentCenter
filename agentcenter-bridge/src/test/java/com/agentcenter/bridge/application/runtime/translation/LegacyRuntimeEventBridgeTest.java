package com.agentcenter.bridge.application.runtime.translation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LegacyRuntimeEventBridgeTest {

    private ObjectMapper objectMapper;
    private LegacyRuntimeEventBridge bridge;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bridge = new LegacyRuntimeEventBridge();
    }

    private RuntimeEventEnvelope envelope(String type) throws Exception {
        JsonNode payload = objectMapper.readTree("{\"type\":\"test\",\"label\":\"value\"}");
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
    }

    @Test
    void conversationDeltaMapsToAssistantDelta() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.CONVERSATION_DELTA);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.ASSISTANT_DELTA, dto.eventType());
        assertEquals("agent_ses_1", dto.sessionId());
        assertEquals(RuntimeEventSource.OPENCODE, dto.eventSource());
    }

    @Test
    void toolStartedMapsToSkillStarted() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.TOOL_STARTED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.SKILL_STARTED, dto.eventType());
    }

    @Test
    void toolCompletedMapsToSkillCompleted() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.TOOL_COMPLETED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.SKILL_COMPLETED, dto.eventType());
    }

    @Test
    void permissionRequestedMapsCorrectly() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.PERMISSION_REQUESTED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.PERMISSION_REQUIRED, dto.eventType());
    }

    @Test
    void runtimeStatusChangedMapsToStatus() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.STATUS, dto.eventType());
    }

    @Test
    void runtimeErrorMapsToError() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.RUNTIME_ERROR);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.ERROR, dto.eventType());
    }

    @Test
    void conversationCompletedMapsToAssistantCompleted() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.CONVERSATION_COMPLETED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals(RuntimeEventType.ASSISTANT_COMPLETED, dto.eventType());
    }

    @Test
    void nullPayloadMapsToEmptyJson() throws Exception {
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA, null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            null, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertEquals("{}", dto.payloadJson());
    }

    @Test
    void occurredAtFallbackWhenNull() throws Exception {
        RuntimeEventEnvelope env = envelope(RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        assertNotNull(dto.createdAt());
    }
}
