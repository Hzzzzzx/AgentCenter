package com.agentcenter.bridge.application.runtime;

/**
 * Data-plane port for conversation operations: session lifecycle, messaging, skill execution.
 */
public interface ConversationRuntimePort {
    String createSession(String workItemId, String agentSessionId);
    String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId);
    SkillRunResult runSkill(String sessionId, String skillName, String inputContext);
    default SkillRunResult runSkill(String sessionId, SkillInvocationRequest request) {
        return runSkill(sessionId, request.skillName(), request.userPrompt());
    }
    void sendMessage(String sessionId, String userMessage);
    void cancel(String sessionId);
    default void registerWorkflowNodeContext(String agentSessionId, String workItemId,
                                               String workflowInstanceId, String workflowNodeInstanceId) {}
}
