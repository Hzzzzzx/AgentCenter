package com.agentcenter.bridge.application.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;

/**
 * Control-plane port for runtime resource lifecycle (skills, MCP config).
 */
public interface RuntimeResourcePort {
    void refreshSkills(RuntimeSkillSnapshot snapshot);
    void refreshMcps();

    List<RuntimeSkillDto> scanSkills();
    String installSkill(String skillName, Path sourceDir);
    void deleteSkillFiles(String relativePath, String skillName);
    String getSkillsRootPath();

    Map<String, Object> readMcpConfig();
    void writeMcpConfig(Map<String, Object> config);
}
