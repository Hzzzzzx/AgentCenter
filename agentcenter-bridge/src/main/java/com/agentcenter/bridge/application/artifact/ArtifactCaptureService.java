package com.agentcenter.bridge.application.artifact;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentSessionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ArtifactCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCaptureService.class);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArtifactMapper artifactMapper;
    private final AgentSessionMapper sessionMapper;
    private final RuntimeEventService runtimeEventService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public ArtifactCaptureService(ArtifactMapper artifactMapper,
                                  AgentSessionMapper sessionMapper,
                                  RuntimeEventService runtimeEventService,
                                  IdGenerator idGenerator,
                                  ObjectMapper objectMapper) {
        this.artifactMapper = artifactMapper;
        this.sessionMapper = sessionMapper;
        this.runtimeEventService = runtimeEventService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public List<ArtifactEntity> captureFromAssistantMessage(AgentMessageEntity message) {
        if (message == null || message.getId() == null || message.getSessionId() == null) {
            return List.of();
        }
        if (!MessageRole.ASSISTANT.name().equals(message.getRole())) {
            return List.of();
        }
        List<ArtifactBlockParser.ArtifactBlock> blocks = ArtifactBlockParser.parse(message.getContent());
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<ArtifactEntity> existing = artifactMapper.findBySourceMessageId(message.getId());
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        AgentSessionEntity session = sessionMapper.findById(message.getSessionId());
        List<ArtifactEntity> captured = new ArrayList<>();
        for (ArtifactBlockParser.ArtifactBlock block : blocks) {
            ArtifactEntity artifact = new ArtifactEntity();
            artifact.setId(idGenerator.nextId());
            artifact.setSessionId(message.getSessionId());
            artifact.setWorkItemId(session != null ? session.getWorkItemId() : null);
            artifact.setWorkflowInstanceId(session != null ? session.getWorkflowInstanceId() : null);
            artifact.setWorkflowNodeInstanceId(message.getWorkflowNodeInstanceId());
            artifact.setArtifactType(block.artifactType().name());
            artifact.setTitle(block.title());
            artifact.setContent(block.content());
            artifact.setFilePath(block.filePath());
            artifact.setVersionNo(1);
            artifact.setSourceType("MESSAGE");
            artifact.setSourceMessageId(message.getId());
            artifact.setCreatedBy("artifact-capture");
            artifact.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            artifactMapper.insert(artifact);
            captured.add(artifact);
            publishCapturedArtifactEvent(artifact, "已保存对话产物");
        }
        return captured;
    }

    public ArtifactEntity captureFromRuntimeArtifact(RuntimeEventEnvelope envelope) {
        if (envelope == null || !RuntimeEventTypes.PROCESS_TRACE.equals(envelope.type())) {
            return null;
        }
        JsonNode payload = envelope.payload();
        if (payload == null || !"artifact".equals(text(payload, "kind"))) {
            return null;
        }
        String artifactId = text(payload, "artifactId");
        if (artifactId.isBlank()) {
            return null;
        }
        ArtifactEntity existing = artifactMapper.findById(artifactId);
        if (existing != null) {
            return existing;
        }

        String sourceEventId = firstNonBlank(envelope.messageId(), text(payload, "messageId"), artifactId);
        List<ArtifactEntity> existingBySource = sourceEventId.isBlank()
                ? List.of()
                : artifactMapper.findBySourceEventId(sourceEventId);
        if (existingBySource != null && !existingBySource.isEmpty()) {
            return existingBySource.get(0);
        }

        String filePath = text(payload, "filePath");
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(artifactId);
        artifact.setSessionId(envelope.agentSessionId());
        artifact.setWorkItemId(envelope.workItemId());
        artifact.setWorkflowInstanceId(envelope.workflowInstanceId());
        artifact.setWorkflowNodeInstanceId(envelope.workflowNodeInstanceId());
        artifact.setArtifactType(typeFromRuntimePart(text(payload, "rawPartType")).name());
        artifact.setTitle(titleFromRuntimePayload(payload, filePath));
        artifact.setContent(null);
        artifact.setStorageUri(filePath.isBlank() ? null : filePath);
        artifact.setFilePath(filePath.isBlank() ? null : filePath);
        artifact.setVersionNo(1);
        artifact.setSourceType("RUNTIME_EVENT");
        artifact.setSourceEventId(sourceEventId.isBlank() ? null : sourceEventId);
        artifact.setCreatedBy(envelope.runtimeType() != null ? envelope.runtimeType().name() : "runtime");
        artifact.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        artifactMapper.insert(artifact);
        return artifact;
    }

    private void publishCapturedArtifactEvent(ArtifactEntity artifact, String summary) {
        if (artifact.getSessionId() == null || artifact.getSessionId().isBlank()) {
            return;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("kind", "artifact");
            payload.put("status", "completed");
            payload.put("title", artifact.getTitle());
            payload.put("summary", summary);
            payload.put("artifactId", artifact.getId());
            payload.put("visibility", "public_summary");
            payload.put("sourceType", artifact.getSourceType());
            if (artifact.getSourceMessageId() != null) {
                payload.put("sourceMessageId", artifact.getSourceMessageId());
            }
            if (artifact.getFilePath() != null) {
                payload.put("filePath", artifact.getFilePath());
            }

            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    artifact.getSessionId(),
                    artifact.getWorkItemId(),
                    artifact.getWorkflowInstanceId(),
                    artifact.getWorkflowNodeInstanceId(),
                    RuntimeEventType.PROCESS_TRACE,
                    RuntimeEventSource.BRIDGE,
                    payload.toString(),
                    OffsetDateTime.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to publish artifact capture event for {}: {}", artifact.getId(), e.getMessage());
        }
    }

    private ArtifactType typeFromRuntimePart(String rawPartType) {
        if ("patch".equalsIgnoreCase(rawPartType)) {
            return ArtifactType.PATCH;
        }
        return ArtifactType.MARKDOWN;
    }

    private String titleFromRuntimePayload(JsonNode payload, String filePath) {
        String title = text(payload, "title");
        if (!title.isBlank() && !"产物变更".equals(title)) {
            return title;
        }
        if (filePath != null && !filePath.isBlank()) {
            int idx = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
            return idx >= 0 ? filePath.substring(idx + 1) : filePath;
        }
        String summary = text(payload, "summary");
        return summary.isBlank() ? "Runtime artifact" : summary;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
