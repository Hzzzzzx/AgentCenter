package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProjectDataSnapshotDto(
        String providerId,
        List<ProjectContextDto> contexts,
        ProjectContextOptionsDto options,
        List<ProjectProviderWorkItemDto> workItems,
        OffsetDateTime syncedAt
) {}
