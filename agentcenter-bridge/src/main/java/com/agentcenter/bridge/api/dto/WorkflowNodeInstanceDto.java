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
        String errorMessage,
        String nodeKind,
        String origin,
        String parentNodeInstanceId,
        String stageKey,
        String skillName,
        String summary,
        String reason,
        Integer sequenceNo
) {}
