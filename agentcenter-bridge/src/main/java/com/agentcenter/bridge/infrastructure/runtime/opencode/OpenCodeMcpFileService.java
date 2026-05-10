package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles OpenCode project-level MCP config file I/O.
 * Extracted from McpRegistryService to keep file knowledge in the infrastructure layer.
 */
@Component
public class OpenCodeMcpFileService {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeMcpFileService.class);

    private final ObjectMapper objectMapper;
    private final String workingDirectory;

    public OpenCodeMcpFileService(
            ObjectMapper objectMapper,
            @Value("${agentcenter.runtime.opencode.serve.working-directory}") String workingDirectory) {
        this.objectMapper = objectMapper;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Reads the OpenCode project config from opencode.json.
     * Legacy .opencode/mcp.json files are only used as an import fallback.
     */
    public Map<String, Object> readMcpConfig() {
        return readMcpConfig(null);
    }

    public Map<String, Object> readMcpConfig(Path projectWorkdir) {
        Path workspace = resolveWorkingDirectory(projectWorkdir);
        Path projectConfigPath = workspace.resolve("opencode.json");
        if (Files.exists(projectConfigPath)) {
            return readConfig(projectConfigPath);
        }

        Path legacyConfigPath = workspace.resolve(".opencode").resolve("mcp.json");
        if (Files.exists(legacyConfigPath)) {
            return readConfig(legacyConfigPath);
        }

        Path agentCenterConfigPath = workspace.resolve(".opencode").resolve("mcp.agentcenter.json");
        if (Files.exists(agentCenterConfigPath)) {
            return readConfig(agentCenterConfigPath);
        }

        return Map.of();
    }

    /**
     * Writes enabled MCP servers into the project opencode.json `mcp` section.
     * Existing project config fields are preserved.
     */
    public void writeMcpConfig(Map<String, Object> config) {
        writeMcpConfig(null, config);
    }

    public void writeMcpConfig(Path projectWorkdir, Map<String, Object> config) {
        try {
            Path configPath = resolveWorkingDirectory(projectWorkdir).resolve("opencode.json");
            Map<String, Object> root = new LinkedHashMap<>();
            if (Files.exists(configPath)) {
                root.putAll(readConfig(configPath));
            }

            Object mcpServers = config.get("mcp");
            if (mcpServers == null) {
                mcpServers = config.get("mcpServers");
            }
            if (mcpServers == null) {
                mcpServers = Map.of();
            }

            root.put("mcp", normalizeMcpServers(mcpServers));
            Files.createDirectories(configPath.getParent());
            Path tempPath = configPath.resolveSibling("opencode.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), root);
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Wrote MCP config to {}", configPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write MCP config", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfig(Path configPath) {
        try {
            String content = Files.readString(configPath);
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MCP config from " + configPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMcpServers(Object mcpServers) {
        if (!(mcpServers instanceof Map<?, ?> rawServers)) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawServers.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> rawConfig)) {
                continue;
            }

            Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) rawConfig);
            config.putIfAbsent("enabled", true);
            if (!config.containsKey("type")) {
                config.put("type", config.containsKey("url") ? "remote" : "local");
            }
            normalized.put(entry.getKey().toString(), config);
        }
        return normalized;
    }

    private Path resolveWorkingDirectory(Path projectWorkdir) {
        if (projectWorkdir != null) {
            Path normalized = projectWorkdir.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return RuntimeWorkspace.resolve(workingDirectory);
    }

}
