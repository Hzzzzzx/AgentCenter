package com.agentcenter.bridge.api.dto;

public record ProjectDataProviderDto(
        String id,
        String name,
        String description,
        boolean active
) {}
