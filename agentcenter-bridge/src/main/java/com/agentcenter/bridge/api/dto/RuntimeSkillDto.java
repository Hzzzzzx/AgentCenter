package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record RuntimeSkillDto(
        String name,
        String description,
        String relativePath,
        String checksum,
        OffsetDateTime updatedAt
) {}
