package com.agentcenter.bridge.application.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class ArtifactCaptureServiceTest {

    private ArtifactMapper artifactMapper;
    private AgentSessionMapper sessionMapper;
    private RuntimeEventService runtimeEventService;
    private IdGenerator idGenerator;
    private ObjectMapper objectMapper;
    private ArtifactCaptureService service;

    @BeforeEach
    void setUp() {
        artifactMapper = mock(ArtifactMapper.class);
        sessionMapper = mock(AgentSessionMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        idGenerator = mock(IdGenerator.class);
        objectMapper = new ObjectMapper();
        service = new ArtifactCaptureService(
                artifactMapper, sessionMapper, runtimeEventService, idGenerator, objectMapper);
    }

    @Test
    void capturesArtifactBlockFromAssistantMessage() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId("session-1");
        session.setWorkItemId("work-1");
        session.setWorkflowInstanceId("workflow-1");
        when(sessionMapper.findById("session-1")).thenReturn(session);
        when(artifactMapper.findBySourceMessageId("msg-1")).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn("art-1");

        AgentMessageEntity message = new AgentMessageEntity();
        message.setId("msg-1");
        message.setSessionId("session-1");
        message.setRole("ASSISTANT");
        message.setWorkflowNodeInstanceId("node-1");
        message.setContent("""
                <!-- AGENTCENTER_ARTIFACT_BEGIN
                title: FE2001 PRD.md
                type: MARKDOWN
                -->
                # FE2001 PRD

                正文。
                <!-- AGENTCENTER_ARTIFACT_END -->
                """);

        var captured = service.captureFromAssistantMessage(message);

        assertThat(captured).hasSize(1);
        verify(artifactMapper).insert(argThat(artifact ->
                "art-1".equals(artifact.getId())
                        && "MESSAGE".equals(artifact.getSourceType())
                        && "msg-1".equals(artifact.getSourceMessageId())
                        && "work-1".equals(artifact.getWorkItemId())
                        && "workflow-1".equals(artifact.getWorkflowInstanceId())
                        && "node-1".equals(artifact.getWorkflowNodeInstanceId())
                        && artifact.getContent().contains("# FE2001 PRD")
        ));
        verify(runtimeEventService).publishEvent(argThat(event ->
                event.eventType() == RuntimeEventType.PROCESS_TRACE
                        && "session-1".equals(event.sessionId())
                        && event.payloadJson().contains("\"artifactId\":\"art-1\"")
        ));
    }

    @Test
    void capturesRuntimeArtifactWithExistingRuntimeArtifactId() throws Exception {
        when(artifactMapper.findById("runtime-art-1")).thenReturn(null);
        when(artifactMapper.findBySourceEventId("runtime-art-1")).thenReturn(List.of());

        var payload = objectMapper.readTree("""
                {
                  "kind": "artifact",
                  "title": "产物变更",
                  "summary": "生成文件 /tmp/report.md",
                  "artifactId": "runtime-art-1",
                  "filePath": "/tmp/report.md",
                  "rawPartType": "file"
                }
                """);
        RuntimeEventEnvelope envelope = new RuntimeEventEnvelope(
                "runtime-event", RuntimeEventTypes.PROCESS_TRACE, null, null, null,
                RuntimeType.OPENCODE, "session-1", "runtime-1", "work-1", null, null,
                payload, OffsetDateTime.now());

        ArtifactEntity captured = service.captureFromRuntimeArtifact(envelope);

        assertThat(captured.getId()).isEqualTo("runtime-art-1");
        verify(artifactMapper).insert(argThat(artifact ->
                "runtime-art-1".equals(artifact.getId())
                        && "RUNTIME_EVENT".equals(artifact.getSourceType())
                        && "report.md".equals(artifact.getTitle())
                        && "/tmp/report.md".equals(artifact.getFilePath())
        ));
    }
}
