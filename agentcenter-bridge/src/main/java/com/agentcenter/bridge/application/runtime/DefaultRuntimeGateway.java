package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * Default gateway implementation. Selects provider by RuntimeType via registry.
 */
@Service
public class DefaultRuntimeGateway implements RuntimeGateway {

    private final RuntimeProviderRegistry registry;

    public DefaultRuntimeGateway(RuntimeProviderRegistry registry) {
        this.registry = registry;
    }

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
    public void refreshSkills(RuntimeType runtimeType, RuntimeSkillSnapshot snapshot) {
        registry.getProvider(runtimeType).refreshSkills(snapshot);
    }

    @Override
    public void refreshMcps(RuntimeType runtimeType) {
        registry.getProvider(runtimeType).refreshMcps();
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
    public List<RuntimeSkillDto> scanSkills(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).scanSkills();
    }

    @Override
    public String installSkill(RuntimeType runtimeType, String skillName, Path sourceDir) {
        return registry.getProvider(runtimeType).installSkill(skillName, sourceDir);
    }

    @Override
    public void deleteSkillFiles(RuntimeType runtimeType, String relativePath, String skillName) {
        registry.getProvider(runtimeType).deleteSkillFiles(relativePath, skillName);
    }

    @Override
    public String getSkillsRootPath(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).getSkillsRootPath();
    }

    @Override
    public Map<String, Object> readMcpConfig(RuntimeType runtimeType) {
        return registry.getProvider(runtimeType).readMcpConfig();
    }

    @Override
    public void writeMcpConfig(RuntimeType runtimeType, Map<String, Object> config) {
        registry.getProvider(runtimeType).writeMcpConfig(config);
    }
}
