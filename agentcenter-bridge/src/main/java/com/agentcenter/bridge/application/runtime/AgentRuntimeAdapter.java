package com.agentcenter.bridge.application.runtime;

public interface AgentRuntimeAdapter {
    String createSession(String workItemId, String agentSessionId);
    default String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        if (runtimeSessionId == null || runtimeSessionId.isBlank()) {
            return createSession(workItemId, agentSessionId);
        }
        return runtimeSessionId;
    }
    SkillRunResult runSkill(String sessionId, String skillName, String inputContext);
    void sendMessage(String sessionId, String userMessage);
    void cancel(String sessionId);
    default void refreshSkills(RuntimeSkillSnapshot snapshot) {
        // Runtime implementations that cache project-level skills can override this hook.
    }
    default void refreshMcps() {
        // Runtime implementations that cache project-level MCP config can override this hook.
    }
}
