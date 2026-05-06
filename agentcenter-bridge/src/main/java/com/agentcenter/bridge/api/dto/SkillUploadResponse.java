package com.agentcenter.bridge.api.dto;

public record SkillUploadResponse(
        RuntimeSkillDetailDto skill,
        RefreshStatus refresh
) {
    public record RefreshStatus(String status, String eventId) {}
}