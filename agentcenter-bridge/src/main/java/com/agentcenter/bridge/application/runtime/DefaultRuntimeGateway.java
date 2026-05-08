package com.agentcenter.bridge.application.runtime;

import org.springframework.stereotype.Service;

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
}
