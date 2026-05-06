package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.workflow.InputPolicy;

import java.util.List;

public record UpdateWorkflowDefinitionRequest(
        String name,
        Boolean isDefault,
        List<Node> nodes
) {
    public record Node(
            String nodeKey,
            String name,
            String skillName,
            InputPolicy inputPolicy,
            ArtifactType outputArtifactType,
            Boolean requiredConfirmation,
            String stageKey,
            String stageGoal,
            List<String> recommendedSkillNames,
            Boolean allowDynamicActions,
            String confirmationPolicy
    ) {}
}
