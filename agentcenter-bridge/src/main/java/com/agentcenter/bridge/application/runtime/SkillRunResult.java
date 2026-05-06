package com.agentcenter.bridge.application.runtime;

public record SkillRunResult(
    boolean success,
    String outputContent,
    String artifactType,
    String errorMessage,
    boolean requiresConfirmation
) {}
