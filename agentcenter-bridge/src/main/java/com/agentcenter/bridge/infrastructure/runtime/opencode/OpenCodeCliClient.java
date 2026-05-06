package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenCodeCliClient {

    private final ObjectMapper objectMapper;
    private final String command;
    private final String model;
    private final String workingDirectory;
    private final Duration timeout;

    public OpenCodeCliClient(ObjectMapper objectMapper,
                             @Value("${agentcenter.runtime.opencode.command:opencode}") String command,
                             @Value("${agentcenter.runtime.opencode.model:}") String model,
                             @Value("${agentcenter.runtime.opencode.working-directory:}") String workingDirectory,
                             @Value("${agentcenter.runtime.opencode.timeout-seconds:180}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.command = command;
        this.model = model;
        this.workingDirectory = workingDirectory;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public OpenCodeChatResult sendMessage(String runtimeSessionId, String userMessage) {
        List<String> args = buildCommand(runtimeSessionId, userMessage);
        Process process;
        try {
            process = new ProcessBuilder(args)
                    .directory(resolveWorkingDirectory().toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            return OpenCodeChatResult.failure(runtimeSessionId,
                    "OpenCode 启动失败：" + e.getMessage());
        }

        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return OpenCodeChatResult.failure(runtimeSessionId, "OpenCode 调用被中断");
        }

        if (!finished) {
            process.destroyForcibly();
            return OpenCodeChatResult.failure(runtimeSessionId,
                    "OpenCode 超时未返回，请检查模型或本地运行时状态");
        }

        String output = outputFuture.join();
        ParsedOpenCodeOutput parsed = parseOutput(output);
        String nextRuntimeSessionId = isBlank(parsed.sessionId()) ? runtimeSessionId : parsed.sessionId();

        if (process.exitValue() != 0) {
            String error = !isBlank(parsed.errorMessage())
                    ? parsed.errorMessage()
                    : firstMeaningfulLine(output);
            return OpenCodeChatResult.failure(nextRuntimeSessionId,
                    "OpenCode 返回错误：" + error);
        }

        if (isBlank(parsed.text())) {
            return OpenCodeChatResult.failure(nextRuntimeSessionId,
                    "OpenCode 已返回但没有可展示的文本输出");
        }

        return OpenCodeChatResult.success(nextRuntimeSessionId, parsed.text());
    }

    private List<String> buildCommand(String runtimeSessionId, String userMessage) {
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add("run");
        args.add("--format");
        args.add("json");
        if (!isBlank(model)) {
            args.add("--model");
            args.add(model);
        }
        if (!isBlank(runtimeSessionId)) {
            args.add("--session");
            args.add(runtimeSessionId);
        }
        args.add(userMessage);
        return args;
    }

    private Path resolveWorkingDirectory() {
        Path configured = isBlank(workingDirectory)
                ? defaultProjectDirectory()
                : Path.of(workingDirectory);
        Path normalized = configured.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return normalized;
        }
        return defaultProjectDirectory();
    }

    private Path defaultProjectDirectory() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if ("agentcenter-bridge".equals(userDir.getFileName().toString()) && userDir.getParent() != null) {
            return userDir.getParent();
        }
        return userDir;
    }

    private String readAll(InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private ParsedOpenCodeOutput parseOutput(String output) {
        String sessionId = "";
        StringBuilder text = new StringBuilder();
        String errorMessage = "";

        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root.hasNonNull("sessionID")) {
                    sessionId = root.path("sessionID").asText();
                }
                JsonNode part = root.path("part");
                if (part.hasNonNull("sessionID")) {
                    sessionId = part.path("sessionID").asText();
                }
                if ("text".equals(root.path("type").asText()) && part.hasNonNull("text")) {
                    text.append(part.path("text").asText());
                }
                if ("error".equals(root.path("type").asText())) {
                    JsonNode error = root.path("error");
                    String message = error.path("data").path("message").asText("");
                    errorMessage = !isBlank(message) ? message : error.path("name").asText("");
                }
            } catch (Exception ignored) {
                // opencode may emit non-JSON diagnostic lines before JSON events.
            }
        }

        return new ParsedOpenCodeOutput(sessionId, text.toString().trim(), errorMessage);
    }

    private String firstMeaningfulLine(String output) {
        for (String line : output.replaceAll("\\u001B\\[[;\\d]*m", "").split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "未知错误";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedOpenCodeOutput(String sessionId, String text, String errorMessage) {}
}
