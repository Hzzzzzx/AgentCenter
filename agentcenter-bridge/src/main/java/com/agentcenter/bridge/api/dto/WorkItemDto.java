package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record WorkItemDto(
        String id,
        String code,
        WorkItemType type,
        String title,
        String description,
        WorkItemStatus status,
        Priority priority,
        String projectId,
        String spaceId,
        String iterationId,
        String assigneeUserId,
        String currentWorkflowInstanceId,
        WorkflowSummaryDto workflowSummary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
