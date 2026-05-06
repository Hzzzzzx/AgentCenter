package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

public record SessionRuntimeResourceDto(
        String projectId,
        Integer skillCount,
        Integer enabledMcpCount,
        Integer mcpToolCount,
        OffsetDateTime lastRefreshedAt,
        Boolean reloadRequired
) {}