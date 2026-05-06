package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record RuntimeResourceAuditDto(
        String id,
        String projectId,
        String resourceType,
        String resourceId,
        String action,
        String status,
        String summary,
        String detailJson,
        String createdBy,
        OffsetDateTime createdAt
) {}