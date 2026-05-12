package com.agentcenter.bridge.api.dto;

import java.util.List;

public record ProjectDataProviderSettingsDto(
        List<ProjectDataProviderDto> providers,
        String activeProviderId
) {}
