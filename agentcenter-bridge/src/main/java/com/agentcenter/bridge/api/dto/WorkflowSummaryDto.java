package com.agentcenter.bridge.api.dto;

import java.util.List;

public record WorkflowSummaryDto(
        String instanceId,
        String status,
        String currentNodeInstanceId,
        String currentStageKey,
        List<NodeSummary> nodes,
        List<StageSummary> stages
) {
    public record NodeSummary(
            String id,
            String definitionName,
            String skillName,
            String status
    ) {}

    public record StageSummary(
            String id,
            String stageKey,
            String name,
            String skillName,
            String status,
            int dynamicNodeCount,
            int recoveryCount,
            int pendingConfirmationCount,
            String latestSummary
    ) {}
}
