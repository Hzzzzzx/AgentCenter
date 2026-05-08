package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.application.runtime.*;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

@Component
public class OpenCodeRuntimeProvider implements RuntimeProvider {

    private static final RuntimeCapabilities CAPABILITIES = new RuntimeCapabilities(
        true,   // conversationStreaming
        true,   // skillLifecycle
        true,   // mcpLifecycle
        true,   // cancelSupported
        RuntimeCapabilities.HTTP,
        RuntimeCapabilities.SSE,
        RuntimeCapabilities.LOCAL_FILE,
        false   // supportsAsyncOperations
    );

    private static final RuntimeDescriptor DESCRIPTOR = new RuntimeDescriptor(
        "OpenCode",
        "HTTP+SSE",
        "Local OpenCode runtime via opencode serve",
        CAPABILITIES
    );

    private final OpenCodeRuntimeAdapter adapter;

    public OpenCodeRuntimeProvider(OpenCodeRuntimeAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public RuntimeType runtimeType() {
        return RuntimeType.OPENCODE;
    }

    @Override
    public RuntimeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String createSession(String workItemId, String agentSessionId) {
        return adapter.createSession(workItemId, agentSessionId);
    }

    @Override
    public String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        return adapter.ensureSession(workItemId, agentSessionId, runtimeSessionId);
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        return adapter.runSkill(sessionId, skillName, inputContext);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        adapter.sendMessage(sessionId, userMessage);
    }

    @Override
    public void cancel(String sessionId) {
        adapter.cancel(sessionId);
    }

    @Override
    public void refreshSkills(RuntimeSkillSnapshot snapshot) {
        adapter.refreshSkills(snapshot);
    }

    @Override
    public void refreshMcps() {
        adapter.refreshMcps();
    }

    @Override
    public void refreshMcps(Path projectWorkdir) {
        adapter.refreshMcps(projectWorkdir);
    }

    @Override
    public List<RuntimeSkillDto> scanSkills() {
        return adapter.scanSkills();
    }

    @Override
    public List<RuntimeSkillDto> scanSkills(Path projectWorkdir) {
        return adapter.scanSkills(projectWorkdir);
    }

    @Override
    public String installSkill(String skillName, Path sourceDir) {
        return adapter.installSkill(skillName, sourceDir);
    }

    @Override
    public String installSkill(Path projectWorkdir, String skillName, Path sourceDir) {
        return adapter.installSkill(projectWorkdir, skillName, sourceDir);
    }

    @Override
    public void deleteSkillFiles(String relativePath, String skillName) {
        adapter.deleteSkillFiles(relativePath, skillName);
    }

    @Override
    public void deleteSkillFiles(Path projectWorkdir, String relativePath, String skillName) {
        adapter.deleteSkillFiles(projectWorkdir, relativePath, skillName);
    }

    @Override
    public String getSkillsRootPath() {
        return adapter.getSkillsRootPath();
    }

    @Override
    public String getSkillsRootPath(Path projectWorkdir) {
        return adapter.getSkillsRootPath(projectWorkdir);
    }

    @Override
    public Map<String, Object> readMcpConfig() {
        return adapter.readMcpConfig();
    }

    @Override
    public Map<String, Object> readMcpConfig(Path projectWorkdir) {
        return adapter.readMcpConfig(projectWorkdir);
    }

    @Override
    public void writeMcpConfig(Map<String, Object> config) {
        adapter.writeMcpConfig(config);
    }

    @Override
    public void writeMcpConfig(Path projectWorkdir, Map<String, Object> config) {
        adapter.writeMcpConfig(projectWorkdir, config);
    }

    @Override
    public void registerWorkflowNodeContext(String agentSessionId, String workItemId,
                                              String workflowInstanceId, String workflowNodeInstanceId) {
        adapter.registerWorkflowNodeContext(agentSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId);
    }
}
