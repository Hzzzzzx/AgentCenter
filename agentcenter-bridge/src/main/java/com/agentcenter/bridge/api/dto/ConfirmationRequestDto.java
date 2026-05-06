package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;

public record ConfirmationRequestDto(
        String id,
        ConfirmationRequestType requestType,
        ConfirmationStatus status,
        String workItemId,
        String workItemCode,
        WorkItemType workItemType,
        String workItemTitle,
        String workflowInstanceId,
        String workflowNodeInstanceId,
        String workflowNodeName,
        String agentSessionId,
        String skillName,
        String title,
        String content,
        String contextSummary,
        String optionsJson,
        Priority priority,
        OffsetDateTime createdAt
) {}
