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
        return createSessionWithContext(runtimeType, RuntimeOperationContext.forSession(workItemId, agentSessionId, null));
    }

    @Override
    public String createSessionWithContext(RuntimeType runtimeType, RuntimeOperationContext context) {
        return registry.getProvider(runtimeType).createSessionWithContext(context);
    }

    @Override
    public String ensureSession(RuntimeType runtimeType, String workItemId, String agentSessionId, String runtimeSessionId) {
        return ensureSessionWithContext(runtimeType, RuntimeOperationContext.forSession(workItemId, agentSessionId, runtimeSessionId));
    }

    @Override
    public String ensureSessionWithContext(RuntimeType runtimeType, RuntimeOperationContext context) {
        return registry.getProvider(runtimeType).ensureSessionWithContext(context);
    }

    @Override
    public SkillRunResult runSkill(RuntimeType runtimeType, String sessionId, String skillName, String inputContext) {
        return runSkill(runtimeType, sessionId,
                new SkillInvocationRequest(skillName, inputContext, null, RuntimeInstructionInjectionMode.USER_PROMPT));
    }

    @Override
    public SkillRunResult runSkill(RuntimeType runtimeType, String sessionId, SkillInvocationRequest request) {
        return runSkillWithContext(runtimeType, RuntimeOperationContext.empty().withRuntimeSessionId(sessionId), request);
    }

    @Override
    public SkillRunResult runSkillWithContext(RuntimeType runtimeType, RuntimeOperationContext context, SkillInvocationRequest request) {
        return registry.getProvider(runtimeType).runSkillWithContext(context, request);
    }

    @Override
    public void sendMessage(RuntimeType runtimeType, String sessionId, String userMessage) {
        sendMessageWithContext(runtimeType, RuntimeOperationContext.empty().withRuntimeSessionId(sessionId), userMessage);
    }

    @Override
    public void sendMessageWithContext(RuntimeType runtimeType, RuntimeOperationContext context, String userMessage) {
        registry.getProvider(runtimeType).sendMessageWithContext(context, userMessage);
    }

    @Override
    public void cancel(RuntimeType runtimeType, String sessionId) {
        cancelWithContext(runtimeType, RuntimeOperationContext.empty().withRuntimeSessionId(sessionId));
    }

    @Override
    public void cancelWithContext(RuntimeType runtimeType, RuntimeOperationContext context) {
        registry.getProvider(runtimeType).cancelWithContext(context);
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
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).installSkill(projectWorkdir, skillName, sourceDir));
    }

    @Override
    public void deleteSkillFiles(RuntimeType runtimeType, String relativePath, String skillName) {
        deleteSkillFiles(runtimeType, null, relativePath, skillName);
    }

    @Override
    public void deleteSkillFiles(RuntimeType runtimeType, Path projectWorkdir, String relativePath, String skillName) {
        trackVoidOperation(
                RuntimeOperationType.SKILL_DELETE.value(), "skill", skillName,
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).deleteSkillFiles(projectWorkdir, relativePath, skillName));
    }

    @Override
    public void refreshSkills(RuntimeType runtimeType, RuntimeSkillSnapshot snapshot) {
        trackVoidOperation(
                RuntimeOperationType.SKILL_SCAN.value(), "skill", null,
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).refreshSkills(snapshot));
    }

    @Override
    public void refreshMcps(RuntimeType runtimeType) {
        refreshMcps(runtimeType, null);
    }

    @Override
    public void refreshMcps(RuntimeType runtimeType, Path projectWorkdir) {
        trackVoidOperation(
                RuntimeOperationType.MCP_REFRESH.value(), "mcp", "mcp_config",
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).refreshMcps(projectWorkdir));
    }

    @Override
    public void writeMcpConfig(RuntimeType runtimeType, Map<String, Object> config) {
        writeMcpConfig(runtimeType, null, config);
    }

    @Override
    public void writeMcpConfig(RuntimeType runtimeType, Path projectWorkdir, Map<String, Object> config) {
        trackVoidOperation(
                RuntimeOperationType.MCP_WRITE_CONFIG.value(), "mcp", "mcp_config",
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).writeMcpConfig(projectWorkdir, config));
    }

    @Override
    public Map<String, Object> readMcpConfig(RuntimeType runtimeType) {
        return readMcpConfig(runtimeType, null);
    }

    @Override
    public Map<String, Object> readMcpConfig(RuntimeType runtimeType, Path projectWorkdir) {
        return trackOperation(
                RuntimeOperationType.MCP_READ_CONFIG.value(), "mcp", "mcp_config",
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).readMcpConfig(projectWorkdir));
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
                runtimeType, RuntimeOperationContext.empty(),
                () -> registry.getProvider(runtimeType).scanSkills(projectWorkdir));
    }

    // --- Operation tracking helpers ---

    // TODO(P6): pass real projectId from callers via RuntimeGateway interface change
    private static final String DEFAULT_PROJECT = "default";

    private <T> T trackOperation(String operationType, String resourceType, String resourceId,
                                  RuntimeType runtimeType, RuntimeOperationContext context, Supplier<T> action) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        String projectId = isBlank(ctx.projectId()) ? DEFAULT_PROJECT : ctx.projectId();
        RuntimeOperationEntity op = operationService.createOperation(
                projectId, runtimeType.name(), operationType, ctx.idempotencyKey(), ctx.messageId(), ctx.correlationId(),
                ctx.agentSessionId(), ctx.runtimeSessionId(), ctx.workItemId(), ctx.workflowInstanceId(),
                ctx.workflowNodeInstanceId(), resourceType, resourceId, null, null, ctx.createdByOrSystem());
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
                                     RuntimeType runtimeType, RuntimeOperationContext context, Runnable action) {
        trackOperation(operationType, resourceType, resourceId, runtimeType, context, () -> {
            action.run();
            return null;
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
