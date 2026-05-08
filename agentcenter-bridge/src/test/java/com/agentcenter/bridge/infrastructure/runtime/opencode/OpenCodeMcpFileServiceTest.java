package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class OpenCodeMcpFileServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenCodeMcpFileService service;

    @BeforeEach
    void setUp() {
        service = new OpenCodeMcpFileService(objectMapper, tempDir.toString());
    }

    // --- readMcpConfig ---

    @Test
    void returnsEmptyMapWhenNoFilesExist() {
        Map<String, Object> config = service.readMcpConfig();
        assertThat(config).isEmpty();
    }

    @Test
    void readsMcpJson() throws IOException {
        Path opencodeDir = tempDir.resolve(".opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("mcp.json"),
                "{\"mcpServers\":{\"server1\":{\"command\":\"node\"}}}");

        Map<String, Object> config = service.readMcpConfig();

        assertThat(config).containsKey("mcpServers");
        @SuppressWarnings("unchecked")
        Map<String, Object> servers = (Map<String, Object>) config.get("mcpServers");
        assertThat(servers).containsKey("server1");
    }

    @Test
    void fallsBackToAgentCenterJson() throws IOException {
        Path opencodeDir = tempDir.resolve(".opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("mcp.agentcenter.json"),
                "{\"mcpServers\":{\"fallback\":{\"command\":\"python\"}}}");

        Map<String, Object> config = service.readMcpConfig();

        assertThat(config).containsKey("mcpServers");
        @SuppressWarnings("unchecked")
        Map<String, Object> servers = (Map<String, Object>) config.get("mcpServers");
        assertThat(servers).containsKey("fallback");
    }

    // --- writeMcpConfig ---

    @Test
    void writesConfigToFile() throws IOException {
        Map<String, Object> config = Map.of(
                "mcpServers", Map.of("myServer", Map.of("command", "node", "args", "server.js"))
        );

        service.writeMcpConfig(config);

        Path configFile = tempDir.resolve(".opencode").resolve("mcp.json");
        assertThat(configFile).exists();
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(content).contains("myServer").contains("node");
    }

    @Test
    void overwritesExistingFile() throws IOException {
        Map<String, Object> first = Map.of("version", "1");
        Map<String, Object> second = Map.of("version", "2");

        service.writeMcpConfig(first);
        service.writeMcpConfig(second);

        Path configFile = tempDir.resolve(".opencode").resolve("mcp.json");
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"version\" : \"2\"");
    }
}
