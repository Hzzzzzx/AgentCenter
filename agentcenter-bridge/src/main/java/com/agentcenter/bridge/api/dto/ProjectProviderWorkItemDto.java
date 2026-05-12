package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record ProjectProviderWorkItemDto(
        String code,
        WorkItemType type,
        String title,
        String description,
        WorkItemStatus status,
        Priority priority,
        String project,
        String space,
        String iteration,
        String assigneeUserId
) {}
