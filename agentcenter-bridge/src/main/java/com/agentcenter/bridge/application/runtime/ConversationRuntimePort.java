package com.agentcenter.bridge.application.runtime;

/**
 * Data-plane port for conversation operations: session lifecycle, messaging, skill execution.
 */
public interface ConversationRuntimePort {
    String createSession(String workItemId, String agentSessionId);
    default String createSessionWithContext(RuntimeOperationContext context) {
        return createSession(context.workItemId(), context.agentSessionId());
    }

    String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId);
    default String ensureSessionWithContext(RuntimeOperationContext context) {
        return ensureSession(context.workItemId(), context.agentSessionId(), context.runtimeSessionId());
    }

    SkillRunResult runSkill(String sessionId, String skillName, String inputContext);
    default SkillRunResult runSkill(String sessionId, SkillInvocationRequest request) {
        return runSkill(sessionId, request.skillName(), request.userPrompt());
    }
    default SkillRunResult runSkillWithContext(RuntimeOperationContext context, SkillInvocationRequest request) {
        return runSkill(context.runtimeSessionId(), request);
    }

    void sendMessage(String sessionId, String userMessage);
    default void sendMessageWithContext(RuntimeOperationContext context, String userMessage) {
        sendMessage(context.runtimeSessionId(), userMessage);
    }

    void cancel(String sessionId);
    default void cancelWithContext(RuntimeOperationContext context) {
        cancel(context.runtimeSessionId());
    }

    default void registerWorkflowNodeContext(String agentSessionId, String workItemId,
                                               String workflowInstanceId, String workflowNodeInstanceId) {}
}
