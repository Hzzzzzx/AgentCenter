package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;

import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;

public record AgentMessageDto(
        String id,
        String sessionId,
        MessageRole role,
        String content,
        ContentFormat contentFormat,
        MessageStatus status,
        int seqNo,
        OffsetDateTime createdAt
) {}
