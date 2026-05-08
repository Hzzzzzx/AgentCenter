package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;

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
