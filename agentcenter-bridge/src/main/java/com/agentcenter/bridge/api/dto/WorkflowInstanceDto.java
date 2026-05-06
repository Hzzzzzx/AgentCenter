package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.agentcenter.bridge.domain.workflow.WorkflowStatus;

public record WorkflowInstanceDto(
        String id,
        String workItemId,
        String workflowDefinitionId,
        WorkflowStatus status,
        String currentNodeInstanceId,
        List<WorkflowNodeInstanceDto> nodes,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {}
