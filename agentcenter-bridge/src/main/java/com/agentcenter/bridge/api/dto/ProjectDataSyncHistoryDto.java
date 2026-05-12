package com.agentcenter.bridge.api.dto;

public record ProjectDataSyncHistoryDto(
        String id,
        String providerId,
        String status,
        Integer contextCount,
        Integer workItemCount,
        String activeProjectContextId,
        String activeProjectSpaceId,
        String activeProjectIterationId,
        String resultJson,
        String errorMessage,
        String startedAt,
        String completedAt
) {}
