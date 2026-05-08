package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenCodeRuntimeEventTranslatorTest {

    private ObjectMapper objectMapper;
    private OpenCodeTranslationState state;
    private OpenCodeRuntimeEventTranslator translator;
    private static final String OPENCODE_SESSION_ID = "opencode_ses_1";
    private static final String AGENT_SESSION_ID = "agent_ses_1";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        state = new OpenCodeTranslationState();
        state.initSession(OPENCODE_SESSION_ID);
        translator = new OpenCodeRuntimeEventTranslator(state);
    }

    private RuntimeTranslationContext fixedContext() {
        return new RuntimeTranslationContext() {
            @Override
            public String getAgentSessionId(String runtimeSessionId) {
                return AGENT_SESSION_ID;
            }

            @Override
            public boolean isUserMessage(String runtimeSessionId, String messageId) {
                return state.isUserMessage(runtimeSessionId, messageId);
            }

            @Override
            public void recordUserMessageId(String runtimeSessionId, String messageId) {
                state.recordUserMessageId(runtimeSessionId, messageId);
            }
        };
    }

    private RuntimeTranslationContext nullAgentContext() {
        return new RuntimeTranslationContext() {
            @Override
            public String getAgentSessionId(String runtimeSessionId) {
                return null;
            }

            @Override
            public boolean isUserMessage(String runtimeSessionId, String messageId) {
                return false;
            }

            @Override
            public void recordUserMessageId(String runtimeSessionId, String messageId) {}
        };
    }

    private RuntimeRawEvent rawEvent(String eventType, String json) throws Exception {
        JsonNode rawJson = objectMapper.readTree(json);
        return new RuntimeRawEvent(RuntimeType.OPENCODE, eventType, rawJson, OPENCODE_SESSION_ID);
    }

    private void assertRuntimeSessionId(RuntimeEventEnvelope env) {
        assertEquals(OPENCODE_SESSION_ID, env.runtimeSessionId(),
            "envelope must carry opencodeSessionId for downstream correlation");
    }

    // --- Test Cases ---

    @Test
    void textDeltaEvent() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Hello",
            "part": {"type": "text", "id": "p1"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.CONVERSATION_DELTA, env.type());
        assertEquals(AGENT_SESSION_ID, env.agentSessionId());
        assertRuntimeSessionId(env);
        assertEquals("Hello", env.payload().path("label").asText());
    }

    @Test
    void textUpdatedWithFullText() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "delta": "",
            "part": {"type": "text", "id": "p2", "text": "Full text"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.CONVERSATION_DELTA, result.get(0).type());
        assertEquals("Full text", result.get(0).payload().path("label").asText());

        // Second call with same part.id should produce nothing (dedup)
        List<RuntimeEventEnvelope> result2 = translator.translate(raw, fixedContext());
        assertTrue(result2.isEmpty());
    }

    @Test
    void userMessageFiltered() throws Exception {
        // First record a user message
        String userMsgJson = """
        {
          "type": "message.updated",
          "properties": {
            "info": {"role": "user", "id": "msg_u1"}
          }
        }
        """;
        translator.translate(rawEvent("message.updated", userMsgJson), fixedContext());

        // Then send a delta with that messageID — should be filtered
        String deltaJson = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "User typed this",
            "part": {"type": "text", "id": "p3", "messageID": "msg_u1"}
          }
        }
        """;
        List<RuntimeEventEnvelope> result = translator.translate(rawEvent("message.part.delta", deltaJson), fixedContext());
        assertTrue(result.isEmpty());
    }

    @Test
    void messageUpdatedRecordsUserMessage() throws Exception {
        String json = """
        {
          "type": "message.updated",
          "properties": {
            "info": {"role": "user", "id": "msg_u1"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        // No envelopes from message.updated itself
        assertTrue(result.isEmpty());

        // But state now knows msg_u1 is a user message
        assertTrue(state.isUserMessage(OPENCODE_SESSION_ID, "msg_u1"));
    }

    @Test
    void toolRunning() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_1", "tool": "Read", "state": {"status": "running"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.TOOL_STARTED, env.type());
        assertRuntimeSessionId(env);
        assertEquals("Read", env.payload().path("label").asText());
        assertEquals("call_1", env.payload().path("toolCallId").asText());
    }

    @Test
    void toolCompleted() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_2", "tool": "Write", "state": {"status": "completed", "output": "done"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        // First time seeing callID: produces both started + completed
        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.TOOL_STARTED, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals(RuntimeEventTypes.TOOL_COMPLETED, result.get(1).type());
        assertEquals("Write", result.get(1).payload().path("label").asText());
        assertEquals("call_2", result.get(1).payload().path("toolCallId").asText());
        assertFalse(result.get(1).payload().path("isError").asBoolean());
    }

    @Test
    void toolError() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_3", "tool": "Bash", "state": {"status": "error", "error": "exit 1"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.TOOL_STARTED, result.get(0).type());
        assertEquals(RuntimeEventTypes.TOOL_COMPLETED, result.get(1).type());
        assertTrue(result.get(1).payload().path("isError").asBoolean());
        assertEquals("exit 1", result.get(1).payload().path("output").asText());
    }

    @Test
    void sessionStatusRunning() throws Exception {
        String json = """
        {
          "type": "session.status",
          "properties": {
            "status": {"type": "busy"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.status", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("running", result.get(0).payload().path("label").asText());
    }

    @Test
    void sessionStatusIdle() throws Exception {
        String json = """
        {
          "type": "session.status",
          "properties": {
            "status": "idle"
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.status", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("idle", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.CONVERSATION_COMPLETED, result.get(1).type());
    }

    @Test
    void sessionIdle() throws Exception {
        String json = """
        {
          "type": "session.idle",
          "properties": {}
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.idle", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals("waiting_user", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.CONVERSATION_COMPLETED, result.get(1).type());
        assertRuntimeSessionId(result.get(1));
    }

    @Test
    void permissionAsked() throws Exception {
        String json = """
        {
          "type": "permission.asked",
          "properties": {
            "id": "perm_1",
            "permission": "file_write",
            "tool": {"tool": "Bash", "name": "Write file"},
            "title": "Allow file write?"
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("permission.asked", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.PERMISSION_REQUESTED, result.get(0).type());
        assertEquals("perm_1", result.get(0).payload().path("permissionId").asText());
        assertEquals("Allow file write?", result.get(0).payload().path("title").asText());
    }

    @Test
    void sessionError() throws Exception {
        String json = """
        {
          "type": "session.error",
          "properties": {
            "error": {
              "data": {"message": "Something went wrong"}
            }
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.error", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_ERROR, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals("failed", result.get(0).payload().path("label").asText());
        assertTrue(result.get(0).payload().path("reason").asText().contains("Something went wrong"));
    }

    @Test
    void unknownEventTypeIgnored() throws Exception {
        String json = """
        {
          "type": "some.unknown.event",
          "properties": {"foo": "bar"}
        }
        """;
        RuntimeRawEvent raw = rawEvent("some.unknown.event", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());
        assertTrue(result.isEmpty());
    }

    @Test
    void nullAgentSessionReturnsEmpty() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Hello",
            "part": {"type": "text", "id": "p1"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, nullAgentContext());
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyEventTypeReturnsEmpty() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {"delta": "Hello", "part": {"type": "text", "id": "p1"}}
        }
        """;
        RuntimeRawEvent raw = new RuntimeRawEvent(RuntimeType.OPENCODE, "", objectMapper.readTree(json), OPENCODE_SESSION_ID);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());
        assertTrue(result.isEmpty());
    }

    @Test
    void toolRunningTwiceProducesOnlyOneStarted() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_dup", "tool": "Grep", "state": {"status": "running"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        translator.translate(raw, fixedContext());
        List<RuntimeEventEnvelope> result2 = translator.translate(raw, fixedContext());

        // Second call with same callID should produce nothing (already running)
        assertTrue(result2.isEmpty());
    }

    @Test
    void sessionErrorFallsBackToName() throws Exception {
        String json = """
        {
          "type": "session.error",
          "properties": {
            "error": {
              "name": "TimeoutError"
            }
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.error", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals("TimeoutError", result.get(0).payload().path("reason").asText());
    }

    @Test
    void permissionUpdatedAlsoHandled() throws Exception {
        String json = """
        {
          "type": "permission.updated",
          "properties": {
            "id": "perm_2",
            "permission": "file_read",
            "tool": {"tool": "Read"},
            "title": "Read permission"
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("permission.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.PERMISSION_REQUESTED, result.get(0).type());
    }

    @Test
    void textDeltaWithEmptyDeltaAndNoTextProducesNothing() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "",
            "part": {"type": "text", "id": "p_empty"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());
        assertTrue(result.isEmpty());
    }

    @Test
    void sessionStatusUnknownStatus() throws Exception {
        String json = """
        {
          "type": "session.status",
          "properties": {
            "status": {"type": "thinking"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.status", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("thinking", result.get(0).payload().path("label").asText());
    }
}
