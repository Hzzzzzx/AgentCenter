package com.agentcenter.bridge.application.runtime.translation;

public interface RuntimeTranslationContext {
    String getAgentSessionId(String runtimeSessionId);
    boolean isUserMessage(String runtimeSessionId, String messageId);
    void recordUserMessageId(String runtimeSessionId, String messageId);

    // Workflow context for process trace enrichment
    String getWorkflowNodeInstanceId(String agentSessionId);
    String getWorkflowInstanceId(String agentSessionId);
    String getWorkItemId(String agentSessionId);
}
