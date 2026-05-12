package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeHttpCommandTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class OpenCodeRuntimeAdapterCreateSessionTest {

    private OpenCodeProcessManager processManager;
    private OpenCodeEventSubscriber eventSubscriber;
    private ObjectMapper objectMapper;
    private OpenCodeSkillFileService skillFileService;
    private OpenCodeMcpFileService mcpFileService;
    private OpenCodeHttpCommandTransport commandTransport;
    private RuntimeEventService runtimeEventService;
    private OpenCodeRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        processManager = mock(OpenCodeProcessManager.class);
        eventSubscriber = mock(OpenCodeEventSubscriber.class);
        objectMapper = new ObjectMapper();
        skillFileService = mock(OpenCodeSkillFileService.class);
        mcpFileService = mock(OpenCodeMcpFileService.class);
        commandTransport = mock(OpenCodeHttpCommandTransport.class);
        runtimeEventService = mock(RuntimeEventService.class);

        when(processManager.isEnabled()).thenReturn(true);
        when(processManager.ensureRunning()).thenReturn("http://127.0.0.1:4097");
        when(processManager.resolveWorkingDirectory()).thenReturn(Path.of("/tmp/project"));

        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                "build", 180);
    }

    @Test
    void createSessionPayloadContainsBaseUrlAndWorkingDirectory() {
        // Prepare a successful ack with a sessionId
        ObjectNode ackPayload = objectMapper.createObjectNode();
        ackPayload.put("sessionId", "ses_test123");
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_test123",
                true, null, ackPayload, null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(ack);

        adapter.createSession("work-item-1", "agent-ses-1");

        RuntimeCommandEnvelope sentCommand = envelopeCaptor.getValue();
        JsonNode payload = sentCommand.payload();

        String baseUrl = payload.path("baseUrl").asText("");
        String workingDirectory = payload.path("workingDirectory").asText("");

        assertFalse(baseUrl.isEmpty(), "baseUrl must not be empty in createSession payload");
        assertFalse(workingDirectory.isEmpty(), "workingDirectory must not be empty in createSession payload");
        assertEquals("http://127.0.0.1:4097", baseUrl);
        assertEquals("/tmp/project", workingDirectory);
    }

    @Test
    void createSessionPayloadAsksForExternalDirectoryPermission() {
        ObjectNode ackPayload = objectMapper.createObjectNode();
        ackPayload.put("sessionId", "ses_permissions");
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_permissions",
                true, null, ackPayload, null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(ack);

        adapter.createSession("work-item-perm", "agent-ses-perm");

        JsonNode permissions = envelopeCaptor.getValue().payload().path("permission");
        assertTrue(permissions.isArray());
        assertEquals("edit", permissions.get(0).path("permission").asText());
        assertEquals("ask", permissions.get(0).path("action").asText());
        assertEquals("external_directory", permissions.get(1).path("permission").asText());
        assertEquals("*", permissions.get(1).path("pattern").asText());
        assertEquals("ask", permissions.get(1).path("action").asText());
    }

    @Test
    void createSessionPayloadContainsTitle() {
        ObjectNode ackPayload = objectMapper.createObjectNode();
        ackPayload.put("sessionId", "ses_test456");
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_test456",
                true, null, ackPayload, null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(ack);

        adapter.createSession("my-work-item", "agent-ses-2");

        RuntimeCommandEnvelope sentCommand = envelopeCaptor.getValue();
        String title = sentCommand.payload().path("title").asText("");
        assertEquals("AgentCenter · my-work-item", title);
    }

    @Test
    void runSkillDoesNotTreatToolOutputAsFinalArtifact() {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                "build", 1);

        ObjectNode ackPayload = objectMapper.createObjectNode();
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_test_tool",
                true, null, ackPayload, null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_test_tool")).thenReturn("agent-timeout");
        when(eventSubscriber.getWorkItemId("agent-timeout")).thenReturn("work-timeout");
        when(eventSubscriber.getWorkflowInstanceId("agent-timeout")).thenReturn("workflow-timeout");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-timeout")).thenReturn("node-timeout");
        AtomicInteger fetchCount = new AtomicInteger();
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_test_tool")))
                .thenAnswer(invocation -> fetchCount.getAndIncrement() == 0
                        ? objectMapper.readTree("[]")
                        : objectMapper.readTree("""
                                [
                                  {
                                    "info": {"id": "msg-tool", "role": "assistant", "finish": "tool-calls"},
                                    "parts": [
                                      {
                                        "type": "tool",
                                        "state": {
                                          "status": "completed",
                                          "output": "# 假工具输出\\n\\n这不是助手最终回复"
                                        }
                                      }
                                    ]
                                  }
                                ]
                                """));

        SkillRunResult result = adapter.runSkill("ses_test_tool", "lld-design", "ctx");

        assertFalse(result.success());
        assertNull(result.outputContent());
        assertTrue(result.errorMessage().contains("没有返回可用输出"));

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        var timeoutEvents = eventCaptor.getAllValues().stream()
                .filter(event -> event.payloadJson() != null
                        && event.payloadJson().contains("\"errorCode\":\"RUNTIME_TIMEOUT\""))
                .toList();
        assertEquals(2, timeoutEvents.size());
        assertTrue(timeoutEvents.stream().anyMatch(event -> event.eventType() == RuntimeEventType.ERROR));
        assertTrue(timeoutEvents.stream().anyMatch(event -> event.eventType() == RuntimeEventType.PROCESS_TRACE));
        assertTrue(timeoutEvents.stream().allMatch(event -> "agent-timeout".equals(event.sessionId())));
    }

    @Test
    void runSkillPublishesAssistantSnapshotDeltasWhilePollingMessages() throws Exception {
        ObjectNode ackPayload = objectMapper.createObjectNode();
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_stream",
                true, null, ackPayload, null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_stream")).thenReturn("agent-stream");
        when(eventSubscriber.getWorkItemId("agent-stream")).thenReturn("work-stream");
        when(eventSubscriber.getWorkflowInstanceId("agent-stream")).thenReturn("workflow-stream");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-stream")).thenReturn("node-stream");
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_stream")))
                .thenReturn(objectMapper.readTree("[]"))
                .thenReturn(objectMapper.readTree("""
                        [
                          {
                            "info": {"id": "msg-stream", "role": "assistant", "finish": ""},
                            "parts": [{"type": "text", "text": "第一段"}]
                          }
                        ]
                        """))
                .thenReturn(objectMapper.readTree("""
                        [
                          {
                            "info": {"id": "msg-stream", "role": "assistant", "finish": "stop"},
                            "parts": [{"type": "text", "text": "第一段第二段"}]
                          }
                        ]
                        """));

        SkillRunResult result = adapter.runSkill("ses_stream", "prd-design", "ctx");

        assertTrue(result.success());
        assertEquals("第一段第二段", result.outputContent());

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        var assistantEvents = eventCaptor.getAllValues().stream()
                .filter(event -> event.eventType() == RuntimeEventType.ASSISTANT_DELTA
                        || event.eventType() == RuntimeEventType.ASSISTANT_COMPLETED)
                .toList();

        assertEquals(3, assistantEvents.size());
        assertEquals(RuntimeEventType.ASSISTANT_DELTA, assistantEvents.get(0).eventType());
        assertTrue(assistantEvents.get(0).payloadJson().contains("\"delta\":\"第一段\""));
        assertEquals(RuntimeEventType.ASSISTANT_DELTA, assistantEvents.get(1).eventType());
        assertTrue(assistantEvents.get(1).payloadJson().contains("\"delta\":\"第二段\""));
        assertEquals(RuntimeEventType.ASSISTANT_COMPLETED, assistantEvents.get(2).eventType());
    }

    @Test
    void sendMessagePublishesPromptDebugEvent() {
        ObjectNode createAckPayload = objectMapper.createObjectNode();
        createAckPayload.put("sessionId", "ses_debug");
        RuntimeAckEnvelope createAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-create-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_debug",
                true, null, createAckPayload, null);

        RuntimeAckEnvelope sendAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-send-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_debug",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(createAck, sendAck);
        when(eventSubscriber.getAgentSessionId("ses_debug")).thenReturn("agent-ses-debug");
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_debug")))
                .thenReturn(objectMapper.createArrayNode());

        adapter.createSession("work-debug", "agent-ses-debug");
        adapter.sendMessage("agent-ses-debug", "请检查当前 prompt 组装");

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        RuntimeEventDto event = eventCaptor.getValue();
        assertEquals("agent-ses-debug", event.sessionId());
        assertEquals(RuntimeEventType.PROCESS_TRACE, event.eventType());
        assertTrue(event.payloadJson().contains("\"kind\":\"prompt_debug\""));
        assertTrue(event.payloadJson().contains("请检查当前 prompt 组装"));
        assertTrue(event.payloadJson().contains("本轮用户消息内包含 Runtime 工作目录边界约束"));
        assertTrue(event.payloadJson().contains("\"opencodePromptAsyncBody\""));
    }

    @Test
    void cancelSendsAbortCommandAndKeepsSessionMapping() {
        ObjectNode createAckPayload = objectMapper.createObjectNode();
        createAckPayload.put("sessionId", "ses_cancel");
        RuntimeAckEnvelope createAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-create-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_cancel",
                true, null, createAckPayload, null);

        RuntimeAckEnvelope cancelAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-cancel-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_cancel",
                true, null, objectMapper.createObjectNode(), null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(createAck, cancelAck);

        adapter.createSession("work-cancel", "agent-cancel");
        adapter.cancel("agent-cancel");

        RuntimeCommandEnvelope cancelCommand = envelopeCaptor.getAllValues().get(1);
        assertEquals("conversation.cancel", cancelCommand.type());
        assertEquals("ses_cancel", cancelCommand.runtimeSessionId());
        assertEquals("http://127.0.0.1:4097", cancelCommand.payload().path("baseUrl").asText(""));
        assertEquals("/tmp/project", cancelCommand.payload().path("workingDirectory").asText(""));
        assertEquals("ses_cancel", adapter.getOpencodeSessionId("agent-cancel"));
        verify(eventSubscriber, never()).unregisterSession(anyString());
    }

    @Test
    void cancelInterruptsWaitingSkillAndLeavesNodeInProgress() throws Exception {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                "build", 30);

        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_wait_cancel",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(ack);
        CountDownLatch pollingStarted = new CountDownLatch(1);
        AtomicInteger fetchCount = new AtomicInteger();
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_wait_cancel")))
                .thenAnswer(invocation -> {
                    if (fetchCount.incrementAndGet() > 1) {
                        pollingStarted.countDown();
                    }
                    return objectMapper.createArrayNode();
                });

        CompletableFuture<SkillRunResult> resultFuture = CompletableFuture.supplyAsync(
                () -> adapter.runSkill("ses_wait_cancel", "prd-design", "ctx"));

        assertTrue(pollingStarted.await(2, TimeUnit.SECONDS));
        adapter.cancel("ses_wait_cancel");

        SkillRunResult result = resultFuture.get(2, TimeUnit.SECONDS);
        assertTrue(result.success());
        assertTrue(result.outputContent().contains("status: IN_PROGRESS"));
        assertTrue(result.outputContent().contains("用户已暂停当前回复"));
    }
}
