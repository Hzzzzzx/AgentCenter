package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record ProjectProviderWorkItemDto(
        String externalId,
        String code,
        WorkItemType type,
        String title,
        String description,
        WorkItemStatus status,
        Priority priority,
        String project,
        String space,
        String iteration,
        String projectContextId,
        String externalProjectId,
        String externalSpaceId,
        String externalIterationId,
        String assigneeUserId,
        String extraJson
) {
    public ProjectProviderWorkItemDto(
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
    ) {
        this(
                code,
                code,
                type,
                title,
                description,
                status,
                priority,
                project,
                space,
                iteration,
                null,
                project,
                space,
                iteration,
                assigneeUserId,
                null
        );
    }
}
