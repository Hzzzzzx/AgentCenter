package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;

/**
 * Default gateway implementation. Selects provider by RuntimeType via registry.
 * Tracks mutating operations through RuntimeOperationService.
 */
@Service
public class DefaultRuntimeGateway implements RuntimeGateway {

    private final RuntimeProviderRegistry registry;
    private final RuntimeOperationService operationService;

    public DefaultRuntimeGateway(RuntimeProviderRegistry registry, RuntimeOperationService operationService) {
        this.registry = registry;
        this.operationService = operationService;
    }

    // --- Untracked methods (session/conversation/metadata) ---

    @Override
    public String createSession(RuntimeType runtimeType, String workItemId, String agentSessionId) {
        return registry.getProvider(runtimeType).createSession(workItemId, agentSessionId);
    }

    @Override
    public String ensureSession(RuntimeType runtimeType, String workItemId, String agentSessionId, String runtimeSessionId) {
        return registry.getProvider(runtimeType).ensureSession(workItemId, agentSessionId, runtimeSessionId);
    }

    @Override
    public SkillRunResult runSkill(RuntimeType runtimeType, String sessionId, String skillName, String inputContext) {
        return registry.getProvider(runtimeType).runSkill(sessionId, skillName, inputContext);
    }

    @Override
    public void sendMessage(RuntimeType runtimeType, String sessionId, String userMessage) {
        registry.getProvider(runtimeType).sendMessage(sessionId, userMessage);
    }

    @Override
    public void cancel(RuntimeType runtimeType, String sessionId) {
        registry.getProvider(runtimeType).cancel(sessionId);
    }

    @Override
    public RuntimeDescriptor describe(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).descriptor();
    }

    @Override
    public RuntimeCapabilities capabilities(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).capabilities();
    }

    @Override
    public String getSkillsRootPath(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).getSkillsRootPath();
    }

    @Override
    public String getSkillsRootPath(RuntimeType runtimeType, Path projectWorkdir) {
        return registry.getProvider(runtimeType).getSkillsRootPath(projectWorkdir);
    }

    // --- Tracked methods (operation lifecycle) ---

    @Override
    public String installSkill(RuntimeType runtimeType, String skillName, Path sourceDir) {
        return installSkill(runtimeType, null, skillName, sourceDir);
    }

    @Override
    public String installSkill(RuntimeType runtimeType, Path projectWorkdir, String skillName, Path sourceDir) {
        return trackOperation(
                RuntimeOperationType.SKILL_INSTALL.value(), "skill", skillName,
                runtimeType, () -> registry.getProvider(runtimeType).installSkill(projectWorkdir, skillName, sourceDir));
    }

    @Override
    public void deleteSkillFiles(RuntimeType runtimeType, String relativePath, String skillName) {
        deleteSkillFiles(runtimeType, null, relativePath, skillName);
    }

    @Override
    public void deleteSkillFiles(RuntimeType runtimeType, Path projectWorkdir, String relativePath, String skillName) {
        trackVoidOperation(
                RuntimeOperationType.SKILL_DELETE.value(), "skill", skillName,
                runtimeType, () -> registry.getProvider(runtimeType).deleteSkillFiles(projectWorkdir, relativePath, skillName));
    }

    @Override
    public void refreshSkills(RuntimeType runtimeType, RuntimeSkillSnapshot snapshot) {
        trackVoidOperation(
                RuntimeOperationType.SKILL_SCAN.value(), "skill", null,
                runtimeType, () -> registry.getProvider(runtimeType).refreshSkills(snapshot));
    }

    @Override
    public void refreshMcps(RuntimeType runtimeType) {
        refreshMcps(runtimeType, null);
    }

    @Override
    public void refreshMcps(RuntimeType runtimeType, Path projectWorkdir) {
        trackVoidOperation(
                RuntimeOperationType.MCP_REFRESH.value(), "mcp", "mcp_config",
                runtimeType, () -> registry.getProvider(runtimeType).refreshMcps(projectWorkdir));
    }

    @Override
    public void writeMcpConfig(RuntimeType runtimeType, Map<String, Object> config) {
        writeMcpConfig(runtimeType, null, config);
    }

    @Override
    public void writeMcpConfig(RuntimeType runtimeType, Path projectWorkdir, Map<String, Object> config) {
        trackVoidOperation(
                RuntimeOperationType.MCP_WRITE_CONFIG.value(), "mcp", "mcp_config",
                runtimeType, () -> registry.getProvider(runtimeType).writeMcpConfig(projectWorkdir, config));
    }

    @Override
    public Map<String, Object> readMcpConfig(RuntimeType runtimeType) {
        return readMcpConfig(runtimeType, null);
    }

    @Override
    public Map<String, Object> readMcpConfig(RuntimeType runtimeType, Path projectWorkdir) {
        return trackOperation(
                RuntimeOperationType.MCP_READ_CONFIG.value(), "mcp", "mcp_config",
                runtimeType, () -> registry.getProvider(runtimeType).readMcpConfig(projectWorkdir));
    }

    @Override
    public void registerWorkflowNodeContext(RuntimeType runtimeType, String agentSessionId, String workItemId,
                                              String workflowInstanceId, String workflowNodeInstanceId) {
        registry.getProvider(runtimeType).registerWorkflowNodeContext(
                agentSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId);
    }

    @Override
    public List<RuntimeSkillDto> scanSkills(RuntimeType runtimeType) {
        return scanSkills(runtimeType, null);
    }

    @Override
    public List<RuntimeSkillDto> scanSkills(RuntimeType runtimeType, Path projectWorkdir) {
        return trackOperation(
                RuntimeOperationType.SKILL_SCAN.value(), "skill", null,
                runtimeType, () -> registry.getProvider(runtimeType).scanSkills(projectWorkdir));
    }

    // --- Operation tracking helpers ---

    // TODO(P6): pass real projectId from callers via RuntimeGateway interface change
    private static final String DEFAULT_PROJECT = "default";

    private <T> T trackOperation(String operationType, String resourceType, String resourceId,
                                  RuntimeType runtimeType, Supplier<T> action) {
        RuntimeOperationEntity op = operationService.createOperation(
                DEFAULT_PROJECT, runtimeType.name(), operationType, null, null, null, null, null,
                null, null, null, resourceType, resourceId, null, null, "system");
        operationService.transition(op.getId(), RuntimeOperationStatus.DISPATCHING);
        try {
            T result = action.get();
            operationService.transition(op.getId(), RuntimeOperationStatus.SUCCEEDED);
            return result;
        } catch (Exception e) {
            operationService.transitionToFailed(op.getId(), "PROVIDER_ERROR", e.getMessage());
            throw e;
        }
    }

    private void trackVoidOperation(String operationType, String resourceType, String resourceId,
                                     RuntimeType runtimeType, Runnable action) {
        RuntimeOperationEntity op = operationService.createOperation(
                DEFAULT_PROJECT, runtimeType.name(), operationType, null, null, null, null, null,
                null, null, null, resourceType, resourceId, null, null, "system");
        operationService.transition(op.getId(), RuntimeOperationStatus.DISPATCHING);
        try {
            action.run();
            operationService.transition(op.getId(), RuntimeOperationStatus.SUCCEEDED);
        } catch (Exception e) {
            operationService.transitionToFailed(op.getId(), "PROVIDER_ERROR", e.getMessage());
            throw e;
        }
    }
}
