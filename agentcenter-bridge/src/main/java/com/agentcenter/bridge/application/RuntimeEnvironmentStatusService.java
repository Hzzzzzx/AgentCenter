package com.agentcenter.bridge.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeEnvironmentStatusDto;
import com.agentcenter.bridge.infrastructure.runtime.opencode.RuntimeWorkspace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RuntimeEnvironmentStatusService {

    private final ObjectMapper objectMapper;
    private final RuntimePathHttpClient runtimePathHttpClient;
    private final String runtimeType;
    private final boolean enabled;
    private final String hostname;
    private final int port;
    private final String configuredWorkingDirectory;

    @Autowired
    public RuntimeEnvironmentStatusService(
            ObjectMapper objectMapper,
            @Value("${agentcenter.runtime.default-type:OPENCODE}") String runtimeType,
            @Value("${agentcenter.runtime.opencode.serve.enabled:true}") boolean enabled,
            @Value("${agentcenter.runtime.opencode.serve.hostname:127.0.0.1}") String hostname,
            @Value("${agentcenter.runtime.opencode.serve.port:4097}") int port,
            @Value("${agentcenter.runtime.opencode.serve.working-directory:${user.dir}/runtime-workspace}") String configuredWorkingDirectory) {
        this(
                objectMapper,
                runtimeType,
                enabled,
                hostname,
                port,
                configuredWorkingDirectory,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build()
        );
    }

    RuntimeEnvironmentStatusService(
            ObjectMapper objectMapper,
            String runtimeType,
            boolean enabled,
            String hostname,
            int port,
            String configuredWorkingDirectory,
            HttpClient httpClient) {
        this(
                objectMapper,
                runtimeType,
                enabled,
                hostname,
                port,
                configuredWorkingDirectory,
                request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        );
    }

    RuntimeEnvironmentStatusService(
            ObjectMapper objectMapper,
            String runtimeType,
            boolean enabled,
            String hostname,
            int port,
            String configuredWorkingDirectory,
            RuntimePathHttpClient runtimePathHttpClient) {
        this.objectMapper = objectMapper;
        this.runtimeType = runtimeType;
        this.enabled = enabled;
        this.hostname = hostname;
        this.port = port;
        this.configuredWorkingDirectory = configuredWorkingDirectory;
        this.runtimePathHttpClient = Objects.requireNonNull(runtimePathHttpClient, "runtimePathHttpClient");
    }

    public RuntimeEnvironmentStatusDto currentStatus() {
        String serverUrl = "http://" + hostname + ":" + port;
        Path workingDirectory = RuntimeWorkspace.resolve(configuredWorkingDirectory);
        String workingDirectoryValue = workingDirectory.toString();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/path"))
                    .header("x-opencode-directory", workingDirectoryValue)
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = runtimePathHttpClient.send(request);
            if (response.statusCode() != 200) {
                return unavailable(serverUrl, workingDirectoryValue, "OpenCode /path 返回 HTTP " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            String serverDirectory = text(body, "directory");
            String serverWorktree = text(body, "worktree");
            boolean workspaceAligned = Objects.equals(workingDirectoryValue, serverDirectory)
                    && Objects.equals(workingDirectoryValue, serverWorktree);
            String message = workspaceAligned
                    ? "OpenCode Server 路径已对齐"
                    : "OpenCode Server 当前目录不一致，请重启 OpenCode Server 或检查启动目录";

            return new RuntimeEnvironmentStatusDto(
                    runtimeType,
                    enabled,
                    serverUrl,
                    true,
                    workingDirectoryValue,
                    workspaceAligned,
                    message
            );
        } catch (Exception e) {
            return unavailable(serverUrl, workingDirectoryValue, "无法连接 OpenCode /path: " + e.getMessage());
        }
    }

    private RuntimeEnvironmentStatusDto unavailable(String serverUrl, String workingDirectory, String message) {
        return new RuntimeEnvironmentStatusDto(
                runtimeType,
                enabled,
                serverUrl,
                false,
                workingDirectory,
                false,
                message
        );
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @FunctionalInterface
    interface RuntimePathHttpClient {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }
}
