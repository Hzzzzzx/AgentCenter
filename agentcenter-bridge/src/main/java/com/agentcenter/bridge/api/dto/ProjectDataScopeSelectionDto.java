package com.agentcenter.bridge.api.dto;

public record ProjectDataScopeSelectionDto(
        String providerId,
        String projectName,
        String externalProjectId,
        String externalSpaceId,
        String externalIterationId
) {}
