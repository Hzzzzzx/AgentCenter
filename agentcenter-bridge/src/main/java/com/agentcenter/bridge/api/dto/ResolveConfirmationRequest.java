package com.agentcenter.bridge.api.dto;

import java.util.Map;

import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;

public record ResolveConfirmationRequest(
        ConfirmationActionType actionType,
        String comment,
        Map<String, Object> payload
) {}
