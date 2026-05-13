package com.agentcenter.bridge.api.dto;

public record UpdateProjectDataScopeRequest(
        String providerId,
        String projectId,
        String spaceId,
        String iterationId,
        String externalProjectId,
        String externalSpaceId,
        String externalIterationId
) {}
