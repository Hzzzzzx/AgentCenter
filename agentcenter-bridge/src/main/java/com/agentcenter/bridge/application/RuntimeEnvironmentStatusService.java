package com.agentcenter.bridge.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeEnvironmentStatusDto;
import com.agentcenter.bridge.infrastructure.runtime.opencode.RuntimeWorkspace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RuntimeEnvironmentStatusService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String runtimeType;
    private final boolean enabled;
    private final String hostname;
    private final int port;
    private final String configuredWorkingDirectory;

    public RuntimeEnvironmentStatusService(
            ObjectMapper objectMapper,
            @Value("${agentcenter.runtime.default-type:OPENCODE}") String runtimeType,
            @Value("${agentcenter.runtime.opencode.serve.enabled:true}") boolean enabled,
            @Value("${agentcenter.runtime.opencode.serve.hostname:127.0.0.1}") String hostname,
            @Value("${agentcenter.runtime.opencode.serve.port:4097}") int port,
            @Value("${agentcenter.runtime.opencode.serve.working-directory:${user.dir}/runtime-workspace}") String configuredWorkingDirectory) {
        this.objectMapper = objectMapper;
        this.runtimeType = runtimeType;
        this.enabled = enabled;
        this.hostname = hostname;
        this.port = port;
        this.configuredWorkingDirectory = configuredWorkingDirectory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public RuntimeEnvironmentStatusDto currentStatus() {
        String serverUrl = "http://" + hostname + ":" + port;
        Path resolvedWorkingDirectory = RuntimeWorkspace.resolve(configuredWorkingDirectory);
        String resolved = resolvedWorkingDirectory.toString();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/path"))
                    .header("x-opencode-directory", resolved)
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return unavailable(serverUrl, resolved, "OpenCode /path 返回 HTTP " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            String serverDirectory = text(body, "directory");
            String serverWorktree = text(body, "worktree");
            boolean isolated = Objects.equals(resolved, serverDirectory) && Objects.equals(resolved, serverWorktree);
            String message = isolated ? "OpenCode server 正在隔离工作区内运行" : "OpenCode server 工作区与 Bridge 解析路径不一致";

            return new RuntimeEnvironmentStatusDto(
                    runtimeType,
                    enabled,
                    serverUrl,
                    configuredWorkingDirectory,
                    resolved,
                    true,
                    serverDirectory,
                    serverWorktree,
                    isolated,
                    message
            );
        } catch (Exception e) {
            return unavailable(serverUrl, resolved, "无法连接 OpenCode /path: " + e.getMessage());
        }
    }

    private RuntimeEnvironmentStatusDto unavailable(String serverUrl, String resolvedWorkingDirectory, String message) {
        return new RuntimeEnvironmentStatusDto(
                runtimeType,
                enabled,
                serverUrl,
                configuredWorkingDirectory,
                resolvedWorkingDirectory,
                false,
                null,
                null,
                false,
                message
        );
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
