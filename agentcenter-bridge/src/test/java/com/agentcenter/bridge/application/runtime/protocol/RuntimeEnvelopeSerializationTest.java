package com.agentcenter.bridge.application.runtime.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class RuntimeEnvelopeSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ---- Command Envelope ----

    @Test
    void commandEnvelope_roundTripsWithAllFields() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"message\":\"hello\"}");
        OffsetDateTime now = OffsetDateTime.now();

        RuntimeCommandEnvelope original = new RuntimeCommandEnvelope(
            RuntimeEnvelopeKind.COMMAND,
            "agentcenter.runtime.v1",
            "conversation.message.send",
            "msg-001",
            "corr-001",
            "op-001",
            "idem-001",
            RuntimeType.OPENCODE,
            "agent-sess-001",
            "runtime-sess-001",
            "project-001",
            "workitem-001",
            "workflow-001",
            "node-001",
            payload,
            now
        );

        String json = objectMapper.writeValueAsString(original);
        RuntimeCommandEnvelope deserialized = objectMapper.readValue(json, RuntimeCommandEnvelope.class);

        assertEquals(original.kind(), deserialized.kind());
        assertEquals(original.protocol(), deserialized.protocol());
        assertEquals(original.type(), deserialized.type());
        assertEquals(original.messageId(), deserialized.messageId());
        assertEquals(original.correlationId(), deserialized.correlationId());
        assertEquals(original.operationId(), deserialized.operationId());
        assertEquals(original.idempotencyKey(), deserialized.idempotencyKey());
        assertEquals(original.runtimeType(), deserialized.runtimeType());
        assertEquals(original.agentSessionId(), deserialized.agentSessionId());
        assertEquals(original.runtimeSessionId(), deserialized.runtimeSessionId());
        assertEquals(original.projectId(), deserialized.projectId());
        assertEquals(original.workItemId(), deserialized.workItemId());
        assertEquals(original.workflowInstanceId(), deserialized.workflowInstanceId());
        assertEquals(original.workflowNodeInstanceId(), deserialized.workflowNodeInstanceId());
        assertEquals(original.payload().toString(), deserialized.payload().toString());
        assertNotNull(deserialized.createdAt());
    }

    @Test
    void commandEnvelope_factoryMethod_producesCorrectKind() throws Exception {
        JsonNode payload = objectMapper.readTree("{}");
        RuntimeCommandEnvelope envelope = RuntimeCommandEnvelope.of(
            "session.ensure", RuntimeType.OPENCODE, "sess-123", payload);

        assertEquals(RuntimeEnvelopeKind.COMMAND, envelope.kind());
        assertEquals("agentcenter.runtime.v1", envelope.protocol());
        assertEquals("session.ensure", envelope.type());
        assertNotNull(envelope.messageId());
        assertEquals(RuntimeType.OPENCODE, envelope.runtimeType());
        assertEquals("sess-123", envelope.runtimeSessionId());
        assertNotNull(envelope.createdAt());
    }

    @Test
    void commandEnvelope_factoryMethod_carriesRuntimeContext() throws Exception {
        JsonNode payload = objectMapper.readTree("{}");
        RuntimeOperationContext context = RuntimeOperationContext.empty()
                .withProjectId("project-1")
                .withOperationId("op-1")
                .withIdempotencyKey("idem-1")
                .withMessageId("msg-1")
                .withCorrelationId("corr-1")
                .withAgentSessionId("agent-1")
                .withRuntimeSessionId("runtime-from-context")
                .withWorkItemId("work-1")
                .withWorkflowContext("workflow-1", "node-1");

        RuntimeCommandEnvelope envelope = RuntimeCommandEnvelope.of(
                "conversation.message.send", RuntimeType.OPENCODE, "runtime-override", payload, context);

        assertEquals("msg-1", envelope.messageId());
        assertEquals("corr-1", envelope.correlationId());
        assertEquals("op-1", envelope.operationId());
        assertEquals("idem-1", envelope.idempotencyKey());
        assertEquals("agent-1", envelope.agentSessionId());
        assertEquals("runtime-override", envelope.runtimeSessionId());
        assertEquals("project-1", envelope.projectId());
        assertEquals("work-1", envelope.workItemId());
        assertEquals("workflow-1", envelope.workflowInstanceId());
        assertEquals("node-1", envelope.workflowNodeInstanceId());
    }

    // ---- Ack Envelope ----

    @Test
    void ackEnvelope_roundTripsWithAllFields() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        RuntimeAckEnvelope original = new RuntimeAckEnvelope(
            RuntimeEnvelopeKind.ACK,
            "agentcenter.runtime.v1",
            null,
            "ack-msg-001",
            "corr-001",
            "op-001",
            RuntimeType.OPENCODE,
            "agent-sess-001",
            "runtime-sess-001",
            true,
            null,
            null,
            now
        );

        String json = objectMapper.writeValueAsString(original);
        RuntimeAckEnvelope deserialized = objectMapper.readValue(json, RuntimeAckEnvelope.class);

        assertEquals(RuntimeEnvelopeKind.ACK, deserialized.kind());
        assertEquals("agentcenter.runtime.v1", deserialized.protocol());
        assertEquals("ack-msg-001", deserialized.messageId());
        assertEquals("corr-001", deserialized.correlationId());
        assertEquals("op-001", deserialized.operationId());
        assertEquals(RuntimeType.OPENCODE, deserialized.runtimeType());
        assertEquals("agent-sess-001", deserialized.agentSessionId());
        assertEquals("runtime-sess-001", deserialized.runtimeSessionId());
        assertTrue(deserialized.success());
        assertNull(deserialized.message());
        assertTrue(deserialized.payload() == null || deserialized.payload().isNull());
        assertNotNull(deserialized.createdAt());
    }

    @Test
    void ackEnvelope_factoryAckMethod_hasSuccessTrue() {
        RuntimeAckEnvelope ack = RuntimeAckEnvelope.ack("corr-123", RuntimeType.OPENCODE);
        assertEquals(RuntimeEnvelopeKind.ACK, ack.kind());
        assertTrue(ack.success());
        assertNull(ack.message());
        assertEquals("corr-123", ack.correlationId());
        assertEquals(RuntimeType.OPENCODE, ack.runtimeType());
        assertNotNull(ack.messageId());
        assertNotNull(ack.createdAt());
    }

    @Test
    void ackEnvelope_factoryMethod_carriesRuntimeContext() {
        RuntimeOperationContext context = RuntimeOperationContext.empty()
                .withOperationId("op-1")
                .withAgentSessionId("agent-1")
                .withRuntimeSessionId("runtime-1");

        RuntimeAckEnvelope ack = RuntimeAckEnvelope.ack("corr-123", RuntimeType.OPENCODE, context);

        assertEquals("op-1", ack.operationId());
        assertEquals("agent-1", ack.agentSessionId());
        assertEquals("runtime-1", ack.runtimeSessionId());
    }

    @Test
    void ackEnvelope_factoryNackMethod_hasSuccessFalse() {
        RuntimeAckEnvelope nack = RuntimeAckEnvelope.nack("corr-456", RuntimeType.MOCK, "something broke");
        assertEquals(RuntimeEnvelopeKind.NACK, nack.kind());
        assertFalse(nack.success());
        assertEquals("something broke", nack.message());
        assertEquals("corr-456", nack.correlationId());
        assertEquals(RuntimeType.MOCK, nack.runtimeType());
        assertNotNull(nack.messageId());
        assertNotNull(nack.createdAt());
    }

    // ---- Event Envelope ----

    @Test
    void eventEnvelope_roundTripsWithAllFields() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"delta\":\"hello\"}");
        OffsetDateTime now = OffsetDateTime.now();

        RuntimeEventEnvelope original = new RuntimeEventEnvelope(
            "agentcenter.runtime.v1",
            "conversation.delta",
            "evt-msg-001",
            "corr-001",
            "op-001",
            RuntimeType.OPENCODE,
            "agent-sess-001",
            "runtime-sess-001",
            "workitem-001",
            "workflow-001",
            "node-001",
            payload,
            now
        );

        String json = objectMapper.writeValueAsString(original);
        RuntimeEventEnvelope deserialized = objectMapper.readValue(json, RuntimeEventEnvelope.class);

        assertEquals(original.protocol(), deserialized.protocol());
        assertEquals(original.type(), deserialized.type());
        assertEquals(original.messageId(), deserialized.messageId());
        assertEquals(original.correlationId(), deserialized.correlationId());
        assertEquals(original.operationId(), deserialized.operationId());
        assertEquals(original.runtimeType(), deserialized.runtimeType());
        assertEquals(original.agentSessionId(), deserialized.agentSessionId());
        assertEquals(original.runtimeSessionId(), deserialized.runtimeSessionId());
        assertEquals(original.workItemId(), deserialized.workItemId());
        assertEquals(original.workflowInstanceId(), deserialized.workflowInstanceId());
        assertEquals(original.workflowNodeInstanceId(), deserialized.workflowNodeInstanceId());
        assertNotNull(deserialized.occurredAt());
    }

    // ---- CommandTypes Constants ----

    @Test
    void commandTypes_allConstantsNonNullNonBlank() throws IllegalAccessException {
        for (Field field : RuntimeCommandTypes.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                String value = (String) field.get(null);
                assertNotNull(value, field.getName() + " is null");
                assertFalse(value.isBlank(), field.getName() + " is blank");
            }
        }
    }

    // ---- EventTypes Constants ----

    @Test
    void eventTypes_allConstantsNonNullNonBlank() throws IllegalAccessException {
        for (Field field : RuntimeEventTypes.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                String value = (String) field.get(null);
                assertNotNull(value, field.getName() + " is null");
                assertFalse(value.isBlank(), field.getName() + " is blank");
            }
        }
    }

    @Test
    void eventTypes_existingConstantsUnchanged() {
        assertEquals("conversation.delta", RuntimeEventTypes.CONVERSATION_DELTA);
        assertEquals("conversation.completed", RuntimeEventTypes.CONVERSATION_COMPLETED);
        assertEquals("tool.started", RuntimeEventTypes.TOOL_STARTED);
        assertEquals("tool.completed", RuntimeEventTypes.TOOL_COMPLETED);
        assertEquals("permission.requested", RuntimeEventTypes.PERMISSION_REQUESTED);
        assertEquals("runtime.status.changed", RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
        assertEquals("runtime.error", RuntimeEventTypes.RUNTIME_ERROR);
    }

    // ---- ProtocolVersion ----

    @Test
    void protocolVersion_v1_equalsExpectedValue() {
        assertEquals("agentcenter.runtime.v1", RuntimeProtocolVersion.V1);
    }
}
