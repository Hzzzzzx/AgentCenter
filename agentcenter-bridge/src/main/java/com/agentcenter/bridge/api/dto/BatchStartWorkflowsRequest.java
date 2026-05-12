package com.agentcenter.bridge.api.dto;

import java.util.List;

import com.agentcenter.bridge.domain.workitem.WorkItemType;

import jakarta.validation.constraints.NotNull;

public record BatchStartWorkflowsRequest(
        @NotNull WorkItemType workItemType,
        List<String> workItemIds,
        Integer limit,
        String mode
) {
    public BatchStartWorkflowsRequest {
        workItemIds = workItemIds == null
                ? List.of()
                : workItemIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .toList();
        if (mode == null) {
            mode = "START_OR_CONTINUE";
        }
    }
}
