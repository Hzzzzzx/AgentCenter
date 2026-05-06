package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record RuntimeSkillVersionDto(
        String id,
        String skillId,
        String versionNo,
        String packageChecksum,
        Long packageSize,
        Integer fileCount,
        String installedRelativePath,
        String manifestJson,
        String skillMdSummary,
        String status,
        String createdBy,
        OffsetDateTime createdAt
) {}