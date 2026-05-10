package com.agentcenter.bridge.api.dto;

public record RuntimeEnvironmentStatusDto(
        String runtimeType,
        boolean enabled,
        String serverUrl,
        String configuredWorkingDirectory,
        String resolvedWorkingDirectory,
        boolean serverReachable,
        String serverDirectory,
        String serverWorktree,
        boolean isolated,
        String message
) {
}
