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
    default void refreshMcps(Path projectWorkdir) { refreshMcps(); }

    List<RuntimeSkillDto> scanSkills();
    default List<RuntimeSkillDto> scanSkills(Path projectWorkdir) { return scanSkills(); }

    String installSkill(String skillName, Path sourceDir);
    default String installSkill(Path projectWorkdir, String skillName, Path sourceDir) {
        return installSkill(skillName, sourceDir);
    }

    void deleteSkillFiles(String relativePath, String skillName);
    default void deleteSkillFiles(Path projectWorkdir, String relativePath, String skillName) {
        deleteSkillFiles(relativePath, skillName);
    }

    String getSkillsRootPath();
    default String getSkillsRootPath(Path projectWorkdir) { return getSkillsRootPath(); }

    Map<String, Object> readMcpConfig();
    default Map<String, Object> readMcpConfig(Path projectWorkdir) { return readMcpConfig(); }

    void writeMcpConfig(Map<String, Object> config);
    default void writeMcpConfig(Path projectWorkdir, Map<String, Object> config) {
        writeMcpConfig(config);
    }
}
