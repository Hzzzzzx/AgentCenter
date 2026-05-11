package com.agentcenter.bridge.api.dto;

public record RuntimeEnvironmentStatusDto(
        String runtimeType,
        boolean enabled,
        String serverUrl,
        boolean serverReachable,
        String workingDirectory,
        boolean workspaceAligned,
        String message
) {
}
