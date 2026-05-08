package com.agentcenter.bridge.application.runtime;

/**
 * Control-plane port for runtime resource lifecycle (skills, MCP config).
 */
public interface RuntimeResourcePort {
    void refreshSkills(RuntimeSkillSnapshot snapshot);
    void refreshMcps();
}
