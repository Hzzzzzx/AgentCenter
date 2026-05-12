package com.agentcenter.bridge.api.dto;

import java.util.List;

import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record BatchStartWorkflowsResponse(
        WorkItemType workItemType,
        int requestedCount,
        int requestedLimit,
        int effectiveLimit,
        int startedCount,
        int skippedCount,
        int failedCount,
        List<ItemResult> results
) {
    public record ItemResult(
            String workItemId,
            String code,
            String status,
            String reason,
            StartWorkflowResponse response
    ) {}
}
