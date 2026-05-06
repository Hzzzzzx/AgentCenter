package com.agentcenter.bridge.api.dto;

import com.agentcenter.bridge.domain.session.ContentFormat;

public record SendMessageRequest(
        String content,
        ContentFormat contentFormat
) {
    public SendMessageRequest {
        if (contentFormat == null) {
            contentFormat = ContentFormat.TEXT;
        }
    }
}
