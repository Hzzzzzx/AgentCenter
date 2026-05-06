package com.agentcenter.bridge.api.dto;

import java.util.List;

import com.agentcenter.bridge.domain.workflow.WorkflowDefinitionStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record WorkflowDefinitionDto(
        String id,
        WorkItemType workItemType,
        String name,
        int versionNo,
        WorkflowDefinitionStatus status,
        boolean isDefault,
        List<WorkflowNodeDefinitionDto> nodes
) {}
