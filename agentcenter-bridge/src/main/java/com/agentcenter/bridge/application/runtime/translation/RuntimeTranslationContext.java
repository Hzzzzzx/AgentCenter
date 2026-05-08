package com.agentcenter.bridge.application.runtime.translation;

public interface RuntimeTranslationContext {
    String getAgentSessionId(String runtimeSessionId);
    boolean isUserMessage(String runtimeSessionId, String messageId);
    void recordUserMessageId(String runtimeSessionId, String messageId);
}
