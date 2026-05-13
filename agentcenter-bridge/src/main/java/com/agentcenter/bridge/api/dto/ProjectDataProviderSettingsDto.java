package com.agentcenter.bridge.api.dto;

import java.util.List;

public record ProjectDataProviderSettingsDto(
        List<ProjectDataProviderDto> providers,
        String activeProviderId,
        String activeProjectContextId,
        String activeProjectSpaceId,
        String activeProjectIterationId,
        String activeExternalProjectId,
        String activeExternalSpaceId,
        String activeExternalIterationId
) {}
