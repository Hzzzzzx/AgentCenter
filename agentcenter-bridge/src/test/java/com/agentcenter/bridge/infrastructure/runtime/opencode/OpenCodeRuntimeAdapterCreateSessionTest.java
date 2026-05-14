package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.ProjectRuntimeWorkspaceResolver;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
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
    private WorkItemMapper workItemMapper;
    private ProjectRuntimeWorkspaceResolver workspaceResolver;
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
        workItemMapper = mock(WorkItemMapper.class);
        workspaceResolver = mock(ProjectRuntimeWorkspaceResolver.class);

        when(processManager.isEnabled()).thenReturn(true);
        when(processManager.ensureRunning()).thenReturn("http://127.0.0.1:4097");
        when(processManager.ensureRunning(any(Path.class))).thenReturn("http://127.0.0.1:4097");
        when(processManager.resolveWorkingDirectory()).thenReturn(Path.of("/tmp/project"));

        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver,
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
    void createSessionUsesProjectWorkspaceResolvedFromWorkItem() {
        WorkItemEntity workItem = new WorkItemEntity();
        workItem.setId("work-project-a");
        workItem.setProjectId("Project-A");
        when(workItemMapper.findById("work-project-a")).thenReturn(workItem);
        when(workspaceResolver.resolve("Project-A")).thenReturn(Path.of("/tmp/project-a"));

        ObjectNode ackPayload = objectMapper.createObjectNode();
        ackPayload.put("sessionId", "ses_project_a");
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_project_a",
                true, null, ackPayload, null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(ack);

        adapter.createSession("work-project-a", "agent-project-a");

        assertEquals("/tmp/project-a", envelopeCaptor.getValue().payload().path("workingDirectory").asText());
        verify(processManager).ensureRunning(Path.of("/tmp/project-a"));
        verify(eventSubscriber).registerSession(
                eq("ses_project_a"), eq("agent-project-a"), eq("http://127.0.0.1:4097"), eq("/tmp/project-a"));
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
                workItemMapper, workspaceResolver,
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
    void runSkillPublishesAssistantCompletedButNoSnapshotDeltas() throws Exception {
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
        var assistantDeltas = eventCaptor.getAllValues().stream()
                .filter(event -> event.eventType() == RuntimeEventType.ASSISTANT_DELTA)
                .toList();
        var assistantCompleted = eventCaptor.getAllValues().stream()
                .filter(event -> event.eventType() == RuntimeEventType.ASSISTANT_COMPLETED)
                .toList();

        // No ASSISTANT_DELTA should be published from snapshot polling
        assertEquals(0, assistantDeltas.size(),
                "polling should not publish ASSISTANT_DELTA events; SSE is the live streaming source");
        // Exactly one ASSISTANT_COMPLETED for the final output
        assertEquals(1, assistantCompleted.size());
    }

    @Test
    void runSkillRetriesInterruptedWaitWithoutResendingPrompt() throws Exception {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver,
                "build", 30, 1, 0);

        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_retry_wait",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_retry_wait")).thenReturn("agent-retry");
        when(eventSubscriber.getWorkItemId("agent-retry")).thenReturn("work-retry");
        when(eventSubscriber.getWorkflowInstanceId("agent-retry")).thenReturn("workflow-retry");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-retry")).thenReturn("node-retry");

        AtomicInteger fetchCount = new AtomicInteger();
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_retry_wait")))
                .thenAnswer(invocation -> {
                    int count = fetchCount.incrementAndGet();
                    if (count == 2) {
                        Thread.currentThread().interrupt();
                        return objectMapper.createArrayNode();
                    }
                    if (count >= 3) {
                        return objectMapper.readTree("""
                                [
                                  {
                                    "info": {"id": "msg-retry", "role": "assistant", "finish": "stop"},
                                    "parts": [{"type": "text", "text": "重试后拿到输出"}]
                                  }
                                ]
                                """);
                    }
                    return objectMapper.createArrayNode();
                });

        SkillRunResult result = adapter.runSkill("ses_retry_wait", "prd-design", "ctx");

        assertTrue(result.success());
        assertEquals("重试后拿到输出", result.outputContent());
        assertFalse(Thread.currentThread().isInterrupted());
        verify(commandTransport, times(1)).send(any());

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> event.eventType() == RuntimeEventType.PROCESS_TRACE
                        && event.payloadJson() != null
                        && event.payloadJson().contains("\"errorCode\":\"RUNTIME_WAIT_INTERRUPTED\"")
                        && event.payloadJson().contains("\"retryAttempt\":1")));
    }

    @Test
    void runSkillRetriesTransientPollFailureBeforeReturningOutput() throws Exception {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver,
                "build", 86400, 1, 0, 2, 0);

        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_poll_retry",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_poll_retry")).thenReturn("agent-poll-retry");
        when(eventSubscriber.getWorkItemId("agent-poll-retry")).thenReturn("work-poll-retry");
        when(eventSubscriber.getWorkflowInstanceId("agent-poll-retry")).thenReturn("workflow-poll-retry");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-poll-retry")).thenReturn("node-poll-retry");

        AtomicInteger fetchCount = new AtomicInteger();
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_poll_retry")))
                .thenAnswer(invocation -> {
                    int count = fetchCount.incrementAndGet();
                    if (count == 1) {
                        throw new RuntimeException("temporary 502");
                    }
                    if (count == 2) {
                        return objectMapper.createArrayNode();
                    }
                    return objectMapper.readTree("""
                            [
                              {
                                "info": {"id": "msg-poll-retry", "role": "assistant", "finish": "stop"},
                                "parts": [{"type": "text", "text": "轮询重试后拿到输出"}]
                              }
                            ]
                            """);
                });

        SkillRunResult result = adapter.runSkill("ses_poll_retry", "prd-design", "ctx");

        assertTrue(result.success());
        assertEquals("轮询重试后拿到输出", result.outputContent());

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> event.eventType() == RuntimeEventType.PROCESS_TRACE
                        && event.payloadJson() != null
                        && event.payloadJson().contains("\"errorCode\":\"RUNTIME_POLL_FAILED\"")
                        && event.payloadJson().contains("\"retryAttempt\":1")));
    }

    @Test
    void runSkillReportsInterruptedWaitSeparatelyAfterRetryExhausted() throws Exception {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver,
                "build", 86400, 1, 0);

        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_retry_exhausted",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_retry_exhausted")).thenReturn("agent-retry-exhausted");
        when(eventSubscriber.getWorkItemId("agent-retry-exhausted")).thenReturn("work-retry-exhausted");
        when(eventSubscriber.getWorkflowInstanceId("agent-retry-exhausted")).thenReturn("workflow-retry-exhausted");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-retry-exhausted")).thenReturn("node-retry-exhausted");

        AtomicInteger fetchCount = new AtomicInteger();
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_retry_exhausted")))
                .thenAnswer(invocation -> {
                    int count = fetchCount.incrementAndGet();
                    if (count >= 2) {
                        Thread.currentThread().interrupt();
                    }
                    return objectMapper.createArrayNode();
                });

        SkillRunResult result;
        try {
            result = adapter.runSkill("ses_retry_exhausted", "prd-design", "ctx");
        } finally {
            Thread.interrupted();
        }

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("等待 Skill `prd-design` 输出时被中断"));
        assertTrue(result.errorMessage().contains("已自动重试 1 次仍未恢复"));
        assertFalse(result.errorMessage().contains("86400 秒内没有返回可用输出"));

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        var waitInterruptedEvents = eventCaptor.getAllValues().stream()
                .filter(event -> event.payloadJson() != null
                        && event.payloadJson().contains("\"errorCode\":\"RUNTIME_WAIT_INTERRUPTED\""))
                .toList();
        assertEquals(3, waitInterruptedEvents.size());
        assertTrue(waitInterruptedEvents.stream().anyMatch(event -> event.eventType() == RuntimeEventType.ERROR));
        assertTrue(waitInterruptedEvents.stream().noneMatch(event ->
                event.payloadJson() != null && event.payloadJson().contains("\"errorCode\":\"RUNTIME_TIMEOUT\"")));
    }

    @Test
    void runSkillReportsPollFailureSeparatelyAfterRetryExhausted() throws Exception {
        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver,
                "build", 86400, 1, 0, 2, 0);

        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_poll_exhausted",
                true, null, objectMapper.createObjectNode(), null);

        when(commandTransport.send(any())).thenReturn(ack);
        when(eventSubscriber.getAgentSessionId("ses_poll_exhausted")).thenReturn("agent-poll-exhausted");
        when(eventSubscriber.getWorkItemId("agent-poll-exhausted")).thenReturn("work-poll-exhausted");
        when(eventSubscriber.getWorkflowInstanceId("agent-poll-exhausted")).thenReturn("workflow-poll-exhausted");
        when(eventSubscriber.getWorkflowNodeInstanceId("agent-poll-exhausted")).thenReturn("node-poll-exhausted");
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_poll_exhausted")))
                .thenThrow(new RuntimeException("gateway reset"));

        SkillRunResult result = adapter.runSkill("ses_poll_exhausted", "prd-design", "ctx");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("读取 Runtime 输出时连续失败"));
        assertTrue(result.errorMessage().contains("已自动重试 2 次仍未恢复"));
        assertTrue(result.errorMessage().contains("gateway reset"));
        assertFalse(result.errorMessage().contains("86400 秒内没有返回可用输出"));

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService, atLeastOnce()).publishEvent(eventCaptor.capture());
        var pollFailureEvents = eventCaptor.getAllValues().stream()
                .filter(event -> event.payloadJson() != null
                        && event.payloadJson().contains("\"errorCode\":\"RUNTIME_POLL_FAILED\""))
                .toList();
        assertEquals(4, pollFailureEvents.size());
        assertTrue(pollFailureEvents.stream().anyMatch(event -> event.eventType() == RuntimeEventType.ERROR));
        assertTrue(pollFailureEvents.stream().noneMatch(event ->
                event.payloadJson() != null && event.payloadJson().contains("\"errorCode\":\"RUNTIME_TIMEOUT\"")));
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
    void refreshSkillsDoesNotRestartServeOrDropExistingSessionMapping() {
        ObjectNode createAckPayload = objectMapper.createObjectNode();
        createAckPayload.put("sessionId", "ses_refresh");
        RuntimeAckEnvelope createAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-create-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_refresh",
                true, null, createAckPayload, null);

        RuntimeAckEnvelope sendAck = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-send-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_refresh",
                true, null, objectMapper.createObjectNode(), null);

        ArgumentCaptor<RuntimeCommandEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RuntimeCommandEnvelope.class);
        when(commandTransport.send(envelopeCaptor.capture())).thenReturn(createAck, sendAck);
        when(eventSubscriber.getAgentSessionId("ses_refresh")).thenReturn("agent-ses-refresh");
        when(commandTransport.fetchMessages(anyString(), anyString(), eq("ses_refresh")))
                .thenReturn(objectMapper.createArrayNode());

        adapter.createSession("work-refresh", "agent-ses-refresh");
        adapter.refreshSkills(new RuntimeSkillSnapshot(
                OffsetDateTime.now(), "/tmp/project", List.of()));
        adapter.sendMessage("agent-ses-refresh", "继续当前会话");

        verify(processManager, never()).restartIfRunning();
        verify(processManager, never()).restartIfRunning(any(Path.class));
        assertEquals(RuntimeCommandTypes.SESSION_ENSURE, envelopeCaptor.getAllValues().get(0).type());
        assertEquals(RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, envelopeCaptor.getAllValues().get(1).type());
        assertEquals("ses_refresh", envelopeCaptor.getAllValues().get(1).runtimeSessionId());
    }

    @Test
    void refreshMcpsDoesNotRestartServe() {
        adapter.refreshMcps(Path.of("/tmp/project"));

        verify(processManager, never()).restartIfRunning();
        verify(processManager, never()).restartIfRunning(any(Path.class));
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
                workItemMapper, workspaceResolver,
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
