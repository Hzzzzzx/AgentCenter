package com.agentcenter.bridge.api.dto;

import jakarta.annotation.Nullable;

public record StartWorkflowRequest(
        @Nullable String workflowDefinitionId,
        String mode
) {
    public StartWorkflowRequest {
        if (mode == null) {
            mode = "START_OR_CONTINUE";
        }
    }
}
