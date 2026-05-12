package com.agentcenter.bridge.api.dto;

public record ProjectContextDto(
        String id,
        String project,
        String cloudeReqProject,
        String space,
        String iteration,
        boolean active
) {}
