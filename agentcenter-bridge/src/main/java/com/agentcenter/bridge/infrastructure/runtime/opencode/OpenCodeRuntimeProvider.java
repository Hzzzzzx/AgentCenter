package com.agentcenter.bridge.infrastructure.runtime.opencode;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.*;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * OPENCODE runtime provider. Wraps the existing OpenCodeRuntimeAdapter
 * to implement the Provider/Port interfaces without changing adapter behavior.
 */
@Component
public class OpenCodeRuntimeProvider implements RuntimeProvider {

    private static final RuntimeCapabilities CAPABILITIES = new RuntimeCapabilities(
        true,   // conversationStreaming — SSE-based assistant delta
        false,  // skillLifecycle — P1: runSkill exists but lifecycle is file-scan driven, not provider-managed
        false,  // mcpLifecycle — P1: refreshMcps exists but config is written directly to .opencode/mcp.json
        true    // cancelSupported
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
}
