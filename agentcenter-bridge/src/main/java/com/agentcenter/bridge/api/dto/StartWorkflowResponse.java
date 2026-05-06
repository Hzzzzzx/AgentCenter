package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record StartWorkflowResponse(
    WorkflowInstanceDto workflowInstance,
    AgentSessionDto session,
    List<ArtifactDto> artifacts,
    List<RuntimeEventDto> events,
    ConfirmationRequestDto confirmation
) {}
