package com.agentcenter.bridge.api.dto;

public record ProjectDataSyncStatsDto(
        int total,
        int created,
        int updated,
        int skipped,
        int failed
) {}
