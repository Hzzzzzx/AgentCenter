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
        objectMapper.findAndRegisterModules();
        bridge = new LegacyRuntimeEventBridge();
    }

    private RuntimeEventEnvelope envelope(String type) throws Exception {
        JsonNode payload = objectMapper.readTree("{\"type\":\"test\",\"label\":\"value\"}");
        return new RuntimeEventEnvelope(
            "runtime-event", type, null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
    }

    // --- Metadata pass-through tests ---

    @Test
    void messageIdFromEnvelopeMergedIntoPayloadJson() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"type\":\"test\",\"label\":\"value\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            "msg_123", "corr_456", "op_789",
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("msg_123", result.get("messageId").asText());
        assertEquals("corr_456", result.get("correlationId").asText());
        assertEquals("op_789", result.get("operationId").asText());
        // original payload fields preserved
        assertEquals("test", result.get("type").asText());
        assertEquals("value", result.get("label").asText());
    }

    @Test
    void toolCallIdFromPayloadPreservedInPayloadJson() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"toolCallId\":\"tc_abc\",\"delta\":\"hello\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            "msg_1", null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("tc_abc", result.get("toolCallId").asText());
        assertEquals("hello", result.get("delta").asText());
    }

    @Test
    void partIdFromPayloadPreservedInPayloadJson() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"partId\":\"part_1\",\"text\":\"hi\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("part_1", result.get("partId").asText());
        assertEquals("hi", result.get("text").asText());
    }

    @Test
    void rawEventTypeAndRawPartTypePreserved() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"rawEventType\":\"content_block_start\",\"rawPartType\":\"thinking\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("content_block_start", result.get("rawEventType").asText());
        assertEquals("thinking", result.get("rawPartType").asText());
    }

    @Test
    void confirmationIdArtifactIdFilePathPreserved() throws Exception {
        JsonNode payload = objectMapper.readTree(
            "{\"confirmationId\":\"conf_1\",\"artifactId\":\"art_2\",\"filePath\":\"/a/b.java\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.TOOL_STARTED,
            null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("conf_1", result.get("confirmationId").asText());
        assertEquals("art_2", result.get("artifactId").asText());
        assertEquals("/a/b.java", result.get("filePath").asText());
    }

    @Test
    void parentStepIdPreserved() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"parentStepId\":\"step_parent\",\"label\":\"task\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.PROCESS_TRACE,
            null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("step_parent", result.get("parentStepId").asText());
        assertEquals("task", result.get("label").asText());
    }

    @Test
    void envelopeMetadataDoesNotOverwritePayloadFields() throws Exception {
        // payload already has messageId=existing_msg
        JsonNode payload = objectMapper.readTree("{\"messageId\":\"existing_msg\",\"label\":\"orig\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            "envelope_msg", null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        // payload field takes precedence over envelope metadata
        assertEquals("existing_msg", result.get("messageId").asText());
        assertEquals("orig", result.get("label").asText());
    }

    @Test
    void nullEnvelopeMetadataDoesNotPollutePayload() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"type\":\"test\",\"label\":\"value\"}");
        RuntimeEventEnvelope env = new RuntimeEventEnvelope(
            "runtime-event", RuntimeEventTypes.CONVERSATION_DELTA,
            null, null, null,
            RuntimeType.OPENCODE, "agent_ses_1", null, null, null, null,
            payload, null);
        RuntimeEventDto dto = bridge.toLegacyEvent(env);

        assertNotNull(dto);
        JsonNode result = objectMapper.readTree(dto.payloadJson());
        assertEquals("test", result.get("type").asText());
        assertEquals("value", result.get("label").asText());
        assertFalse(result.has("messageId") && result.get("messageId").isNull());
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
