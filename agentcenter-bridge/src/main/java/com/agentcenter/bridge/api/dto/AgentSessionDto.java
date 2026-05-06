package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.SessionStatus;
import com.agentcenter.bridge.domain.session.SessionType;

public record AgentSessionDto(
        String id,
        SessionType sessionType,
        String title,
        String workItemId,
        String workflowInstanceId,
        RuntimeType runtimeType,
        SessionStatus status,
        String workingDirectory,
        OffsetDateTime createdAt
) {}
