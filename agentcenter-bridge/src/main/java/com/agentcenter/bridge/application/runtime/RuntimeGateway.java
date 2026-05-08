package com.agentcenter.bridge.application.runtime;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * Application layer's single entry point for Runtime access.
 * Delegates to the appropriate provider via the registry.
 */
public interface RuntimeGateway {
    String createSession(RuntimeType runtimeType, String workItemId, String agentSessionId);
    String ensureSession(RuntimeType runtimeType, String workItemId, String agentSessionId, String runtimeSessionId);
    SkillRunResult runSkill(RuntimeType runtimeType, String sessionId, String skillName, String inputContext);
    void sendMessage(RuntimeType runtimeType, String sessionId, String userMessage);
    void cancel(RuntimeType runtimeType, String sessionId);
    void refreshSkills(RuntimeType runtimeType, RuntimeSkillSnapshot snapshot);
    void refreshMcps(RuntimeType runtimeType);
    RuntimeDescriptor describe(RuntimeType runtimeType);
    RuntimeCapabilities capabilities(RuntimeType runtimeType);
}
