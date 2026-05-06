package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.workflow.InputPolicy;

public record WorkflowNodeDefinitionDto(
        String id,
        String nodeKey,
        String name,
        int orderNo,
        String skillName,
        InputPolicy inputPolicy,
        ArtifactType outputArtifactType,
        boolean requiredConfirmation
) {}
