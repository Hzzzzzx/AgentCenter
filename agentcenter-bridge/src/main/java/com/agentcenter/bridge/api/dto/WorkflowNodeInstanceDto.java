package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.workflow.WorkflowNodeStatus;

public record WorkflowNodeInstanceDto(
        String id,
        String nodeDefinitionId,
        WorkflowNodeStatus status,
        String inputArtifactId,
        String outputArtifactId,
        String agentSessionId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage
) {}
