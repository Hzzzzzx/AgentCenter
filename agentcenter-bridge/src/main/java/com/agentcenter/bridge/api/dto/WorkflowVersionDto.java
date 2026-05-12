package com.agentcenter.bridge.api.dto;

public record WorkflowVersionDto(
        int versionNo,
        boolean current,
        WorkflowInstanceDto workflowInstance,
        AgentSessionDto session,
        int artifactCount
) {}
