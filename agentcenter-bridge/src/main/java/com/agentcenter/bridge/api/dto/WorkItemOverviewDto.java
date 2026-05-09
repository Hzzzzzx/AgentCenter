package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkItemOverviewDto(
        String source,
        OffsetDateTime refreshedAt,
        List<TypeStat> stats
) {
    public record TypeStat(
            String type,
            int total,
            int runningCount,
            int waitingCount,
            int blockedCount,
            int unstartedCount,
            int completedCount,
            int completedNodeCount,
            int totalNodeCount,
            int completionRate,
            List<NodeDistribution> nodeDistribution
    ) {}

    public record NodeDistribution(
            String label,
            int count,
            int priority
    ) {}
}
