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

            @Override
            public String getWorkflowNodeInstanceId(String agentSessionId) { return null; }

            @Override
            public String getWorkflowInstanceId(String agentSessionId) { return null; }

            @Override
            public String getWorkItemId(String agentSessionId) { return null; }
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

            @Override
            public String getWorkflowNodeInstanceId(String agentSessionId) { return null; }

            @Override
            public String getWorkflowInstanceId(String agentSessionId) { return null; }

            @Override
            public String getWorkItemId(String agentSessionId) { return null; }
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
        assertEquals("Hello", env.payload().path("delta").asText());
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
        assertEquals("Full text", result.get(0).payload().path("delta").asText());

        // Second call with same part.id should produce nothing (dedup)
        List<RuntimeEventEnvelope> result2 = translator.translate(raw, fixedContext());
        assertTrue(result2.isEmpty());
    }

    @Test
    void textUpdatedWithGrowingFullTextEmitsOnlySuffix() throws Exception {
        RuntimeRawEvent first = rawEvent("message.part.updated", """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "text", "id": "p-grow", "text": "Hello"}
          }
        }
        """);
        RuntimeRawEvent second = rawEvent("message.part.updated", """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "text", "id": "p-grow", "text": "Hello world"}
          }
        }
        """);

        List<RuntimeEventEnvelope> firstResult = translator.translate(first, fixedContext());
        List<RuntimeEventEnvelope> secondResult = translator.translate(second, fixedContext());

        assertEquals(1, firstResult.size());
        assertEquals("Hello", firstResult.get(0).payload().path("delta").asText());
        assertEquals(1, secondResult.size());
        assertEquals(" world", secondResult.get(0).payload().path("delta").asText());
    }

    @Test
    void textDeltaBeforeFullTextSnapshotDoesNotDuplicatePreviousDelta() throws Exception {
        RuntimeRawEvent delta = rawEvent("message.part.delta", """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Hello",
            "part": {"type": "text", "id": "p-mixed"}
          }
        }
        """);
        RuntimeRawEvent snapshot = rawEvent("message.part.updated", """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "text", "id": "p-mixed", "text": "Hello world"}
          }
        }
        """);

        List<RuntimeEventEnvelope> deltaResult = translator.translate(delta, fixedContext());
        List<RuntimeEventEnvelope> snapshotResult = translator.translate(snapshot, fixedContext());

        assertEquals(1, deltaResult.size());
        assertEquals("Hello", deltaResult.get(0).payload().path("delta").asText());
        assertEquals(1, snapshotResult.size());
        assertEquals(" world", snapshotResult.get(0).payload().path("delta").asText());
    }

    @Test
    void opencodeDeltaWithoutNestedPartUsesPreviouslyRecordedPartMetadata() throws Exception {
        RuntimeRawEvent partCreated = rawEvent("message.part.updated", """
        {
          "type": "message.part.updated",
          "properties": {
            "sessionID": "opencode_ses_1",
            "part": {
              "id": "prt_text_1",
              "messageID": "msg_assistant_1",
              "sessionID": "opencode_ses_1",
              "type": "text",
              "text": ""
            }
          }
        }
        """);
        RuntimeRawEvent firstDelta = rawEvent("message.part.delta", """
        {
          "type": "message.part.delta",
          "properties": {
            "sessionID": "opencode_ses_1",
            "messageID": "msg_assistant_1",
            "partID": "prt_text_1",
            "field": "text",
            "delta": "一"
          }
        }
        """);
        RuntimeRawEvent secondDelta = rawEvent("message.part.delta", """
        {
          "type": "message.part.delta",
          "properties": {
            "sessionID": "opencode_ses_1",
            "messageID": "msg_assistant_1",
            "partID": "prt_text_1",
            "field": "text",
            "delta": "\\n二"
          }
        }
        """);

        assertTrue(translator.translate(partCreated, fixedContext()).isEmpty());
        List<RuntimeEventEnvelope> firstResult = translator.translate(firstDelta, fixedContext());
        List<RuntimeEventEnvelope> secondResult = translator.translate(secondDelta, fixedContext());

        assertEquals(1, firstResult.size());
        assertEquals(RuntimeEventTypes.CONVERSATION_DELTA, firstResult.get(0).type());
        assertEquals("一", firstResult.get(0).payload().path("delta").asText());
        assertEquals("message.part.delta", firstResult.get(0).payload().path("rawEventType").asText());
        assertEquals("text", firstResult.get(0).payload().path("rawPartType").asText());
        assertEquals("prt_text_1", firstResult.get(0).payload().path("partId").asText());
        assertEquals(1, secondResult.size());
        assertEquals("\n二", secondResult.get(0).payload().path("delta").asText());
    }

    @Test
    void reasoningDeltaWithoutNestedPartDoesNotBecomeAssistantText() throws Exception {
        RuntimeRawEvent reasoningPart = rawEvent("message.part.updated", """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {
              "id": "prt_reasoning_1",
              "messageID": "msg_assistant_1",
              "type": "reasoning",
              "text": ""
            }
          }
        }
        """);
        RuntimeRawEvent reasoningDelta = rawEvent("message.part.delta", """
        {
          "type": "message.part.delta",
          "properties": {
            "messageID": "msg_assistant_1",
            "partID": "prt_reasoning_1",
            "field": "text",
            "delta": "Thinking"
          }
        }
        """);

        assertTrue(translator.translate(reasoningPart, fixedContext()).isEmpty());
        List<RuntimeEventEnvelope> result = translator.translate(reasoningDelta, fixedContext());

        assertTrue(result.stream().noneMatch(env -> RuntimeEventTypes.CONVERSATION_DELTA.equals(env.type())));
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

        assertEquals(2, result.size());
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.TOOL_STARTED, env.type());
        assertRuntimeSessionId(env);
        assertEquals("Read", env.payload().path("label").asText());
        assertEquals("call_1", env.payload().path("toolCallId").asText());
        RuntimeEventEnvelope trace = result.get(1);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, trace.type());
        assertEquals("tool_call", trace.payload().path("kind").asText());
        assertEquals("running", trace.payload().path("status").asText());
        assertEquals("Read", trace.payload().path("toolName").asText());
        assertEquals("call_1", trace.payload().path("toolCallId").asText());
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

        // First time seeing callID: produces started + process_trace + completed + process_trace
        assertEquals(4, result.size());
        assertEquals(RuntimeEventTypes.TOOL_STARTED, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("tool_call", result.get(1).payload().path("kind").asText());
        assertEquals("running", result.get(1).payload().path("status").asText());
        assertEquals(RuntimeEventTypes.TOOL_COMPLETED, result.get(2).type());
        assertEquals("Write", result.get(2).payload().path("label").asText());
        assertEquals("call_2", result.get(2).payload().path("toolCallId").asText());
        assertFalse(result.get(2).payload().path("isError").asBoolean());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(3).type());
        assertEquals("completed", result.get(3).payload().path("status").asText());
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

        assertEquals(4, result.size());
        assertEquals(RuntimeEventTypes.TOOL_STARTED, result.get(0).type());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("running", result.get(1).payload().path("status").asText());
        assertEquals(RuntimeEventTypes.TOOL_COMPLETED, result.get(2).type());
        assertTrue(result.get(2).payload().path("isError").asBoolean());
        assertEquals("exit 1", result.get(2).payload().path("output").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(3).type());
        assertEquals("failed", result.get(3).payload().path("status").asText());
        assertEquals("Bash 调用失败", result.get(3).payload().path("summary").asText());
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

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("running", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("node_status", result.get(1).payload().path("kind").asText());
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

        assertEquals(3, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("idle", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("completed", result.get(1).payload().path("status").asText());
        assertEquals(RuntimeEventTypes.CONVERSATION_COMPLETED, result.get(2).type());
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

        assertEquals(3, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals("waiting_user", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals(RuntimeEventTypes.CONVERSATION_COMPLETED, result.get(2).type());
        assertRuntimeSessionId(result.get(2));
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
            "title": "Allow file write?",
            "args": {"filePath": "C:\\\\Users\\\\alice\\\\workspace\\\\demo\\\\.opencode\\\\skills\\\\planner\\\\SKILL.md"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("permission.asked", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.PERMISSION_REQUESTED, result.get(0).type());
        assertEquals("perm_1", result.get(0).payload().path("permissionId").asText());
        assertEquals("perm_opencode_ses_1_perm_1", result.get(0).payload().path("confirmationId").asText());
        assertEquals("Allow file write?", result.get(0).payload().path("title").asText());
        assertEquals("C:\\Users\\alice\\workspace\\demo\\.opencode\\skills\\planner\\SKILL.md",
                result.get(0).payload().path("filePath").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("confirmation", result.get(1).payload().path("kind").asText());
    }

    @Test
    void questionAskedCreatesQuestionRequestedEnvelope() throws Exception {
        String json = """
        {
          "type": "question.asked",
          "properties": {
            "id": "q_1",
            "sessionID": "opencode_ses_1",
            "tool": {"messageID": "msg_1", "callID": "call_question"},
            "questions": [
              {
                "header": "方案",
                "question": "请选择推进方案",
                "multiple": false,
                "custom": true,
                "options": [
                  {"label": "快速验证", "description": "先走最小验证"},
                  {"label": "严格校验", "description": "补充回归验证"}
                ]
              }
            ]
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("question.asked", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        RuntimeEventEnvelope question = result.get(0);
        assertEquals(RuntimeEventTypes.QUESTION_REQUESTED, question.type());
        assertRuntimeSessionId(question);
        assertEquals("q_1", question.payload().path("requestId").asText());
        assertEquals("question_opencode_ses_1_q_1", question.payload().path("confirmationId").asText());
        assertEquals("call_question", question.payload().path("toolCallId").asText());
        assertEquals("请选择推进方案", question.payload().path("questions").get(0).path("question").asText());

        RuntimeEventEnvelope trace = result.get(1);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, trace.type());
        assertEquals("confirmation", trace.payload().path("kind").asText());
        assertEquals("question", trace.payload().path("toolName").asText());
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

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_ERROR, result.get(0).type());
        assertRuntimeSessionId(result.get(0));
        assertEquals("failed", result.get(0).payload().path("label").asText());
        assertTrue(result.get(0).payload().path("reason").asText().contains("Something went wrong"));
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
        assertEquals("error", result.get(1).payload().path("kind").asText());
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

        assertEquals(2, result.size());
        assertEquals("TimeoutError", result.get(0).payload().path("reason").asText());
        assertEquals("TimeoutError", result.get(1).payload().path("summary").asText());
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

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.PERMISSION_REQUESTED, result.get(0).type());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
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

        assertEquals(2, result.size());
        assertEquals(RuntimeEventTypes.RUNTIME_STATUS_CHANGED, result.get(0).type());
        assertEquals("thinking", result.get(0).payload().path("label").asText());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(1).type());
    }

    @Test
    void reasoningWithoutVisibilityDoesNotEmitSyntheticSummary() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Private chain of thought content here",
            "part": {"type": "reasoning", "text": "Full private reasoning text that should not be exposed"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertTrue(result.isEmpty());
    }

    @Test
    void reasoningWithPublicVisibilityExposesContent() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "",
            "part": {"type": "reasoning", "visibility": "public_summary", "summary": "Analyzing the user's request"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, result.get(0).type());
        assertEquals("Analyzing the user's request", result.get(0).payload().path("summary").asText());
    }

    @Test
    void reasoningWithExplicitSummaryFieldExposesShortSummary() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Long raw reasoning delta that should be ignored",
            "part": {"type": "reasoning", "summary": "Short structured summary"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        assertEquals("Short structured summary", result.get(0).payload().path("summary").asText());
    }

    // --- Projection Metadata Tests ---

    @Test
    void textDeltaIncludesProjectionMetadata() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "Hello with metadata",
            "part": {"type": "text", "id": "p_meta1", "messageID": "msg_meta1"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        JsonNode payload = result.get(0).payload();
        assertEquals("message.part.delta", payload.path("rawEventType").asText());
        assertEquals("text", payload.path("rawPartType").asText());
        assertEquals("msg_meta1", payload.path("messageId").asText());
        assertEquals("p_meta1", payload.path("partId").asText());
    }

    @Test
    void toolRunningIncludesProjectionMetadata() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_meta1", "tool": "Read", "name": "Read File", "state": {"status": "running"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        // TOOL_STARTED
        RuntimeEventEnvelope started = result.get(0);
        assertEquals(RuntimeEventTypes.TOOL_STARTED, started.type());
        assertEquals("message.part.updated", started.payload().path("rawEventType").asText());
        assertEquals("tool", started.payload().path("rawPartType").asText());
        assertEquals("call_meta1", started.payload().path("toolCallId").asText());
        assertEquals("Read", started.payload().path("rawName").asText());
        assertEquals("Read File", started.payload().path("displayName").asText());

        // PROCESS_TRACE for tool_call
        RuntimeEventEnvelope trace = result.get(1);
        assertEquals("message.part.updated", trace.payload().path("rawEventType").asText());
        assertEquals("tool", trace.payload().path("rawPartType").asText());
    }

    @Test
    void reasoningIncludesProjectionMetadata() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "delta": "",
            "part": {"type": "reasoning", "id": "r_part1", "messageID": "msg_r1", "visibility": "public_summary", "summary": "Analyzing metadata"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.delta", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(1, result.size());
        JsonNode payload = result.get(0).payload();
        assertEquals("message.part.delta", payload.path("rawEventType").asText());
        assertEquals("reasoning", payload.path("rawPartType").asText());
        assertEquals("msg_r1", payload.path("messageId").asText());
        assertEquals("r_part1", payload.path("partId").asText());
    }

    @Test
    void permissionIncludesConfirmationId() throws Exception {
        String json = """
        {
          "type": "permission.asked",
          "properties": {
            "id": "perm_conf1",
            "permission": "file_write",
            "tool": {"tool": "Bash"},
            "title": "Allow write?"
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("permission.asked", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        JsonNode payload = result.get(0).payload();
        assertEquals("permission.asked", payload.path("rawEventType").asText());
        assertEquals("perm_conf1", payload.path("permissionId").asText());
        assertEquals("perm_opencode_ses_1_perm_conf1", payload.path("confirmationId").asText());
    }

    @Test
    void sessionStatusIncludesRawEventType() throws Exception {
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

        assertEquals(2, result.size());
        assertEquals("session.status", result.get(0).payload().path("rawEventType").asText());
        assertEquals("session.status", result.get(1).payload().path("rawEventType").asText());
    }

    @Test
    void sessionErrorIncludesRawEventType() throws Exception {
        String json = """
        {
          "type": "session.error",
          "properties": {
            "error": {"data": {"message": "err"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.error", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(2, result.size());
        assertEquals("session.error", result.get(0).payload().path("rawEventType").asText());
    }

    @Test
    void sessionIdleIncludesRawEventType() throws Exception {
        String json = """
        {
          "type": "session.idle",
          "properties": {}
        }
        """;
        RuntimeRawEvent raw = rawEvent("session.idle", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertEquals(3, result.size());
        assertEquals("session.idle", result.get(0).payload().path("rawEventType").asText());
    }

    // --- New Mapping Tests: file, patch, artifact, retry, subtask, agent-handoff ---

    @Test
    void filePartProducesArtifactTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "file", "id": "file_p1", "path": "/src/main/App.java", "content": "public class App {}"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "file part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("artifact", env.payload().path("kind").asText());
        assertEquals("/src/main/App.java", env.payload().path("filePath").asText());
        assertEquals("file_p1", env.payload().path("artifactId").asText());
        assertEquals("message.part.updated", env.payload().path("rawEventType").asText());
        assertEquals("file", env.payload().path("rawPartType").asText());
    }

    @Test
    void patchPartProducesArtifactTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "patch", "id": "patch_p1", "path": "/src/main/App.java", "diff": "--- a/App.java\\n+++ b/App.java\\n@@ -1 +1 @@"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "patch part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("artifact", env.payload().path("kind").asText());
        assertEquals("/src/main/App.java", env.payload().path("filePath").asText());
        assertEquals("patch_p1", env.payload().path("artifactId").asText());
        assertEquals("patch", env.payload().path("rawPartType").asText());
    }

    @Test
    void artifactPartProducesArtifactTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "artifact", "id": "art_p1", "name": "report.pdf", "url": "/files/report.pdf"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "artifact part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("artifact", env.payload().path("kind").asText());
        assertEquals("art_p1", env.payload().path("artifactId").asText());
        assertEquals("report.pdf", env.payload().path("filePath").asText());
    }

    @Test
    void retryEventProducesProcessTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "retry", "id": "retry_p1", "attempt": 2, "maxAttempts": 3, "reason": "timeout"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "retry part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("retry", env.payload().path("kind").asText());
        assertEquals("running", env.payload().path("status").asText());
        assertEquals("retry_p1", env.payload().path("operationId").asText());
        assertEquals("message.part.updated", env.payload().path("rawEventType").asText());
        assertEquals("retry", env.payload().path("rawPartType").asText());
    }

    @Test
    void subtaskPartProducesProcessTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "subtask", "id": "sub_p1", "name": "Analyze codebase", "status": "running"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "subtask part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("subtask", env.payload().path("kind").asText());
        assertEquals("running", env.payload().path("status").asText());
        assertEquals("sub_p1", env.payload().path("operationId").asText());
        assertEquals("subtask", env.payload().path("rawPartType").asText());
    }

    @Test
    void agentHandoffPartProducesProcessTrace() throws Exception {
        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "agent-handoff", "id": "ah_p1", "targetAgent": "code-reviewer", "parentStepId": "step_42"}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, fixedContext());

        assertFalse(result.isEmpty(), "agent-handoff part should produce at least one event");
        RuntimeEventEnvelope env = result.get(0);
        assertEquals(RuntimeEventTypes.PROCESS_TRACE, env.type());
        assertEquals("agent_handoff", env.payload().path("kind").asText());
        assertEquals("running", env.payload().path("status").asText());
        assertEquals("step_42", env.payload().path("parentStepId").asText());
        assertEquals("agent-handoff", env.payload().path("rawPartType").asText());
    }

    @Test
    void processTraceCarriesWorkflowNodeInstanceId() throws Exception {
        RuntimeTranslationContext contextWithWorkflow = new RuntimeTranslationContext() {
            @Override public String getAgentSessionId(String runtimeSessionId) { return AGENT_SESSION_ID; }
            @Override public boolean isUserMessage(String runtimeSessionId, String messageId) { return false; }
            @Override public void recordUserMessageId(String runtimeSessionId, String messageId) {}
            @Override public String getWorkflowNodeInstanceId(String agentSessionId) { return "node_123"; }
            @Override public String getWorkflowInstanceId(String agentSessionId) { return "wf_456"; }
            @Override public String getWorkItemId(String agentSessionId) { return "wi_789"; }
        };

        String json = """
        {
          "type": "message.part.updated",
          "properties": {
            "part": {"type": "tool", "callID": "call_wf", "tool": "Read", "state": {"status": "running"}}
          }
        }
        """;
        RuntimeRawEvent raw = rawEvent("message.part.updated", json);
        List<RuntimeEventEnvelope> result = translator.translate(raw, contextWithWorkflow);

        RuntimeEventEnvelope trace = result.stream()
                .filter(e -> RuntimeEventTypes.PROCESS_TRACE.equals(e.type()))
                .findFirst().orElse(null);
        assertNotNull(trace);
        assertEquals("node_123", trace.workflowNodeInstanceId());
        assertEquals("wf_456", trace.workflowInstanceId());
        assertEquals("wi_789", trace.workItemId());
    }
}
