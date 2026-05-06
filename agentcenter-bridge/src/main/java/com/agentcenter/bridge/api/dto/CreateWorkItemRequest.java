package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record CreateWorkItemRequest(
        WorkItemType type,
        String title,
        String description,
        Priority priority,
        String projectId,
        String spaceId,
        String iterationId,
        String assigneeUserId
) {}
