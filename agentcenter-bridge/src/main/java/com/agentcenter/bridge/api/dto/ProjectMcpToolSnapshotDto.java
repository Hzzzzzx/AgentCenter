package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record ProjectMcpToolSnapshotDto(
        String id,
        String projectId,
        String mcpServerId,
        String toolName,
        String description,
        String inputSchemaJson,
        Integer snapshotVersion,
        String status,
        OffsetDateTime scannedAt
) {}