package com.agentcenter.bridge.infrastructure.runtime.opencode;

public record OpenCodeChatResult(
        boolean ok,
        String runtimeSessionId,
        String assistantText,
        String errorMessage
) {
    public static OpenCodeChatResult success(String runtimeSessionId, String assistantText) {
        return new OpenCodeChatResult(true, runtimeSessionId, assistantText, null);
    }

    public static OpenCodeChatResult failure(String runtimeSessionId, String errorMessage) {
        return new OpenCodeChatResult(false, runtimeSessionId, "", errorMessage);
    }
}
