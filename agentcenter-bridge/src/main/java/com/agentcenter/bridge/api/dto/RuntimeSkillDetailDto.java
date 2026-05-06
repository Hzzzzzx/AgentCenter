package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record RuntimeSkillDetailDto(
        String id,
        String projectId,
        String name,
        String displayName,
        String description,
        String currentVersionId,
        String status,
        String source,
        String relativePath,
        String checksum,
        String validationStatus,
        String validationMessage,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String version,
        Integer referenceCount
) {}