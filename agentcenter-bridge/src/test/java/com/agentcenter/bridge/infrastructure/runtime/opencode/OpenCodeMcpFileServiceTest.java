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
    void readsProjectOpencodeJsonFirst() throws IOException {
        Files.writeString(tempDir.resolve("opencode.json"),
                "{\"mcp\":{\"server1\":{\"command\":\"node\"}}}");

        Map<String, Object> config = service.readMcpConfig();

        assertThat(config).containsKey("mcp");
        @SuppressWarnings("unchecked")
        Map<String, Object> servers = (Map<String, Object>) config.get("mcp");
        assertThat(servers).containsKey("server1");
    }

    @Test
    void fallsBackToLegacyMcpJson() throws IOException {
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
    void writesMcpSectionToProjectOpencodeJson() throws IOException {
        Map<String, Object> config = Map.of(
                "mcpServers", Map.of("myServer", Map.of("command", "node", "args", "server.js"))
        );

        service.writeMcpConfig(config);

        Path configFile = tempDir.resolve("opencode.json");
        assertThat(configFile).exists();
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("\"mcp\"")
                .contains("myServer")
                .contains("node")
                .contains("\"type\" : \"local\"")
                .contains("\"enabled\" : true");
    }

    @Test
    void preservesExistingProjectConfigFields() throws IOException {
        Files.writeString(tempDir.resolve("opencode.json"),
                "{\"autoupdate\":false,\"mcp\":{\"old\":{\"command\":\"node\"}}}");
        Map<String, Object> next = Map.of(
                "mcp", Map.of("newServer", Map.of("command", "python"))
        );

        service.writeMcpConfig(next);

        Path configFile = tempDir.resolve("opencode.json");
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("\"autoupdate\" : false")
                .contains("newServer")
                .contains("\"type\" : \"local\"")
                .contains("\"enabled\" : true")
                .doesNotContain("old");
    }
}
