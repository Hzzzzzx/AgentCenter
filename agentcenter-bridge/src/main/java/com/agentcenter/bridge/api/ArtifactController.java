package com.agentcenter.bridge.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.ArtifactDto;
import com.agentcenter.bridge.application.ProjectRuntimeWorkspaceResolver;
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;

@RestController
@RequestMapping("/api")
public class ArtifactController {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_INLINE_FILE_BYTES = 2_000_000L;

    private final ArtifactMapper artifactMapper;
    private final WorkItemMapper workItemMapper;
    private final ProjectRuntimeWorkspaceResolver workspaceResolver;

    public ArtifactController(ArtifactMapper artifactMapper,
                              WorkItemMapper workItemMapper,
                              ProjectRuntimeWorkspaceResolver workspaceResolver) {
        this.artifactMapper = artifactMapper;
        this.workItemMapper = workItemMapper;
        this.workspaceResolver = workspaceResolver;
    }

    @GetMapping("/artifacts/{id}")
    public ArtifactDto getArtifact(@PathVariable String id) {
        ArtifactEntity artifact = artifactMapper.findById(id);
        if (artifact == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact not found: " + id);
        }
        return toDto(artifact);
    }

    @GetMapping("/work-items/{workItemId}/artifacts")
    public List<ArtifactDto> listWorkItemArtifacts(@PathVariable String workItemId) {
        return artifactMapper.findByWorkItemId(workItemId).stream()
                .sorted(Comparator.comparing(ArtifactEntity::getCreatedAt,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .map(this::toDto)
                .toList();
    }

    private ArtifactDto toDto(ArtifactEntity artifact) {
        return new ArtifactDto(
                artifact.getId(),
                artifact.getWorkItemId(),
                artifact.getWorkflowInstanceId(),
                artifact.getWorkflowNodeInstanceId(),
                ArtifactType.valueOf(artifact.getArtifactType()),
                artifact.getTitle(),
                resolveContent(artifact),
                parseDateTime(artifact.getCreatedAt())
        );
    }

    private String resolveContent(ArtifactEntity artifact) {
        if (artifact.getContent() != null && !artifact.getContent().isBlank()) {
            return artifact.getContent();
        }
        return readInlineArtifactFile(artifact);
    }

    private String readInlineArtifactFile(ArtifactEntity artifact) {
        String value = firstNonBlank(artifact.getStorageUri(), artifact.getFilePath());
        if (value.isBlank()) {
            return artifact.getContent();
        }
        try {
            Path file = Path.of(value).toAbsolutePath().normalize();
            Path workspace = workspaceRoot(artifact).toAbsolutePath().normalize();
            if (!file.startsWith(workspace) || !Files.isRegularFile(file)) {
                return artifact.getContent();
            }
            if (Files.size(file) > MAX_INLINE_FILE_BYTES || !isInlineTextArtifact(file)) {
                return artifact.getContent();
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return artifact.getContent();
        }
    }

    private Path workspaceRoot(ArtifactEntity artifact) {
        String projectId = null;
        if (artifact.getWorkItemId() != null && !artifact.getWorkItemId().isBlank()) {
            WorkItemEntity workItem = workItemMapper.findById(artifact.getWorkItemId());
            projectId = workItem != null ? workItem.getProjectId() : null;
        }
        return workspaceResolver.resolve(projectId);
    }

    private boolean isInlineTextArtifact(Path file) {
        String name = file.getFileName() != null
                ? file.getFileName().toString().toLowerCase()
                : "";
        return name.endsWith(".md")
                || name.endsWith(".markdown")
                || name.endsWith(".txt")
                || name.endsWith(".json")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".patch")
                || name.endsWith(".diff");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SQLITE_DATETIME).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return OffsetDateTime.parse(value);
        }
    }
}
