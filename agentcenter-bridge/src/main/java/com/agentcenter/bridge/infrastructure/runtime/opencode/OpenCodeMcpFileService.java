package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles all .opencode/mcp.json file I/O for the OpenCode runtime.
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
     * Reads the MCP config from .opencode/mcp.json (falling back to .opencode/mcp.agentcenter.json).
     * Returns an empty map if neither file exists.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readMcpConfig() {
        Path configPath = Path.of(workingDirectory).resolve(".opencode").resolve("mcp.json");
        if (!Files.exists(configPath)) {
            configPath = Path.of(workingDirectory).resolve(".opencode").resolve("mcp.agentcenter.json");
        }
        if (!Files.exists(configPath)) {
            return Map.of();
        }

        try {
            String content = Files.readString(configPath);
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MCP config from " + configPath, e);
        }
    }

    /**
     * Writes the MCP config to .opencode/mcp.json using atomic file replacement.
     * Creates parent directories if needed.
     */
    public void writeMcpConfig(Map<String, Object> config) {
        try {
            Path configPath = Path.of(workingDirectory).toAbsolutePath().normalize()
                    .resolve(".opencode").resolve("mcp.json");
            Files.createDirectories(configPath.getParent());
            Path tempPath = configPath.resolveSibling("mcp.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), config);
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Wrote MCP config to {}", configPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write MCP config", e);
        }
    }
}
