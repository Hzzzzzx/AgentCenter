package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;

public record UpdateWorkItemRequest(
        String title,
        String description,
        WorkItemStatus status,
        Priority priority,
        String assigneeUserId
) {}
