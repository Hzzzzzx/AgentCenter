package com.agentcenter.bridge.api.dto;

import jakarta.annotation.Nullable;

public record RestartWorkflowRequest(
        @Nullable String workflowDefinitionId,
        String mode,
        @Nullable String reason
) {
    public RestartWorkflowRequest {
        if (mode == null) {
            mode = "START_OR_CONTINUE";
        }
    }
}
