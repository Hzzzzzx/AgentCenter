package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
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

    List<RuntimeSkillDto> scanSkills(RuntimeType runtimeType);
    default List<RuntimeSkillDto> scanSkills(RuntimeType runtimeType, Path projectWorkdir) {
        return scanSkills(runtimeType);
    }

    String installSkill(RuntimeType runtimeType, String skillName, Path sourceDir);
    default String installSkill(RuntimeType runtimeType, Path projectWorkdir, String skillName, Path sourceDir) {
        return installSkill(runtimeType, skillName, sourceDir);
    }

    void deleteSkillFiles(RuntimeType runtimeType, String relativePath, String skillName);
    default void deleteSkillFiles(RuntimeType runtimeType, Path projectWorkdir, String relativePath, String skillName) {
        deleteSkillFiles(runtimeType, relativePath, skillName);
    }

    String getSkillsRootPath(RuntimeType runtimeType);
    default String getSkillsRootPath(RuntimeType runtimeType, Path projectWorkdir) {
        return getSkillsRootPath(runtimeType);
    }

    Map<String, Object> readMcpConfig(RuntimeType runtimeType);
    default Map<String, Object> readMcpConfig(RuntimeType runtimeType, Path projectWorkdir) {
        return readMcpConfig(runtimeType);
    }

    void writeMcpConfig(RuntimeType runtimeType, Map<String, Object> config);
    default void writeMcpConfig(RuntimeType runtimeType, Path projectWorkdir, Map<String, Object> config) {
        writeMcpConfig(runtimeType, config);
    }

    void registerWorkflowNodeContext(RuntimeType runtimeType, String agentSessionId, String workItemId,
                                      String workflowInstanceId, String workflowNodeInstanceId);

    default void refreshMcps(RuntimeType runtimeType, Path projectWorkdir) {
        refreshMcps(runtimeType);
    }
}
