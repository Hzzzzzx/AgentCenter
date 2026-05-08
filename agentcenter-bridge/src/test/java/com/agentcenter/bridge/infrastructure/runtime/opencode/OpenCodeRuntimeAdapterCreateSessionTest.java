package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
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
    private OpenCodeRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        processManager = mock(OpenCodeProcessManager.class);
        eventSubscriber = mock(OpenCodeEventSubscriber.class);
        objectMapper = new ObjectMapper();
        skillFileService = mock(OpenCodeSkillFileService.class);
        mcpFileService = mock(OpenCodeMcpFileService.class);
        commandTransport = mock(OpenCodeHttpCommandTransport.class);

        when(processManager.isEnabled()).thenReturn(true);
        when(processManager.ensureRunning()).thenReturn("http://127.0.0.1:4097");
        when(processManager.resolveWorkingDirectory()).thenReturn(Path.of("/tmp/project"));

        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport,
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
                skillFileService, mcpFileService, commandTransport,
                "build", 1);

        ObjectNode ackPayload = objectMapper.createObjectNode();
        RuntimeAckEnvelope ack = new RuntimeAckEnvelope(
                null, "agentcenter.runtime.v1", null,
                "ack-msg-id", "corr-id", null,
                RuntimeType.OPENCODE, null, "ses_test_tool",
                true, null, ackPayload, null);

        when(commandTransport.send(any())).thenReturn(ack);
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
    }
}
