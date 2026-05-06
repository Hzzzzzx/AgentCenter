package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ProjectMcpServerDto(
        String id,
        String projectId,
        String name,
        String serverType,
        String status,
        Map<String, Object> configSummary,
        String configChecksum,
        String lastHealthStatus,
        String lastHealthMessage,
        OffsetDateTime lastCheckedAt,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer toolCount
) {}