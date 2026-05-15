package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;

public interface AgentRuntimeAdapter {
    String createSession(String workItemId, String agentSessionId);
    default String createSessionWithContext(RuntimeOperationContext context) {
        return createSession(context.workItemId(), context.agentSessionId());
    }

    default String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        if (runtimeSessionId == null || runtimeSessionId.isBlank()) {
            return createSession(workItemId, agentSessionId);
        }
        return runtimeSessionId;
    }
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

    default void refreshSkills(RuntimeSkillSnapshot snapshot) {}
    default void refreshMcps() {}

    default List<RuntimeSkillDto> scanSkills() { return List.of(); }
    default String installSkill(String skillName, Path sourceDir) { return ""; }
    default void deleteSkillFiles(String relativePath, String skillName) {}
    default String getSkillsRootPath() { return ""; }
    default Map<String, Object> readMcpConfig() { return Map.of(); }
    default void writeMcpConfig(Map<String, Object> config) {}
    default void registerWorkflowNodeContext(String agentSessionId, String workItemId,
                                              String workflowInstanceId, String workflowNodeInstanceId) {}
}
