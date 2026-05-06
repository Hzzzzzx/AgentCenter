package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.artifact.ArtifactType;

public record ArtifactDto(
        String id,
        String workItemId,
        String workflowInstanceId,
        String workflowNodeInstanceId,
        ArtifactType artifactType,
        String title,
        String content,
        OffsetDateTime createdAt
) {}
