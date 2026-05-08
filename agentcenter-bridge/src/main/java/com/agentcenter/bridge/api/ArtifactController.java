package com.agentcenter.bridge.api;

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
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;

@RestController
@RequestMapping("/api")
public class ArtifactController {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArtifactMapper artifactMapper;

    public ArtifactController(ArtifactMapper artifactMapper) {
        this.artifactMapper = artifactMapper;
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
                artifact.getContent(),
                parseDateTime(artifact.getCreatedAt())
        );
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
