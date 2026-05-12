package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manages the lifecycle of the {@code opencode serve} process.
 * Starts it on first use, monitors health, and shuts it down on application exit.
 */
@Component
public class OpenCodeProcessManager {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeProcessManager.class);
    private static final long READY_TIMEOUT_MS = 15_000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 250L;

    private final String command;
    private final String hostname;
    private final int port;
    private final String workingDirectory;
    private final boolean enabled;

    private final ReentrantLock startLock = new ReentrantLock();
    private volatile Process process;
    private volatile String baseUrl;
    private volatile boolean started;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OpenCodeProcessManager(
            @Value("${agentcenter.runtime.opencode.serve.command:opencode}") String command,
            @Value("${agentcenter.runtime.opencode.serve.hostname:127.0.0.1}") String hostname,
            @Value("${agentcenter.runtime.opencode.serve.port:4097}") int port,
            @Value("${agentcenter.runtime.opencode.serve.working-directory:${user.dir}/runtime-workspace}") String workingDirectory,
            @Value("${agentcenter.runtime.opencode.serve.enabled:true}") boolean enabled) {
        this.command = command;
        this.hostname = hostname;
        this.port = port;
        this.workingDirectory = workingDirectory;
        this.enabled = enabled;
    }

    /**
     * Ensures the opencode serve process is running and returns the base URL.
     * Starts the process if not already running.
     *
     * @return base URL of opencode serve (e.g. "http://127.0.0.1:4097")
     * @throws IllegalStateException if opencode serve cannot be started or is not enabled
     */
    public String ensureRunning() {
        return ensureRunning(resolveWorkingDirectory());
    }

    public String ensureRunning(Path requiredWorkingDirectory) {
        if (!enabled) {
            throw new IllegalStateException("OpenCode serve adapter is disabled in configuration");
        }
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);
        if (started && isAlive()) {
            return baseUrl;
        }
        startLock.lock();
        try {
            if (started && isAlive()) {
                return baseUrl;
            }
            startProcess(cwd);
            return baseUrl;
        } finally {
            startLock.unlock();
        }
    }

    /**
     * Checks if the opencode serve process is alive and healthy.
     */
    public boolean isHealthy() {
        return isHealthy(resolveWorkingDirectory());
    }

    public boolean isHealthy(Path requiredWorkingDirectory) {
        if (!started || !isAlive()) {
            return false;
        }
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(OpenCodeEndpoint.uri(baseUrl, "/path"))
                    .header("x-opencode-directory", cwd.toString())
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("OpenCode serve health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the base URL if the process has been started.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns the configured working directory as an absolute path.
     */
    public Path resolveWorkingDirectory() {
        return RuntimeWorkspace.resolve(workingDirectory);
    }

    /**
     * Returns whether the adapter is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shuts down the opencode serve process.
     */
    public void shutdown() {
        if (process != null && process.isAlive()) {
            log.info("Shutting down opencode serve process (PID: {})", process.pid());
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        started = false;
        process = null;
    }

    public void restartIfRunning() {
        restartIfRunning(resolveWorkingDirectory());
    }

    public void restartIfRunning(Path requiredWorkingDirectory) {
        if (!enabled) {
            return;
        }
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);
        if (!started && !probeReady(cwd)) {
            return;
        }
        startLock.lock();
        try {
            shutdown();
            startProcess(cwd);
        } finally {
            startLock.unlock();
        }
    }

    private void startProcess(Path requiredWorkingDirectory) {
        baseUrl = "http://" + hostname + ":" + port;
        OpenCodeEndpoint.uri(baseUrl, "/path");
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);

        // First, check if opencode serve is already running on the target port.
        // This avoids spawning a conflicting process when one is already active.
        if (probeReady(cwd)) {
            log.info("opencode serve already running at {}, reusing existing instance", baseUrl);
            started = true;
            return;
        }
        if (isPortOpen()) {
            throw new IllegalStateException(
                    "opencode serve port " + port
                            + " is occupied but did not respond to /path. Restart opencode serve or free the port.");
        }

        String resolvedCommand = resolveCommand();
        if (resolvedCommand == null) {
            throw new IllegalStateException(
                    "Cannot find opencode executable. Install opencode or configure agentcenter.runtime.opencode.serve.command");
        }

        log.info("Starting opencode serve: {} serve --hostname {} --port {} --print-logs --log-level WARN (cwd={})",
                resolvedCommand, hostname, port, cwd);

        ProcessBuilder pb = new ProcessBuilder(buildCommandLine(
                resolvedCommand,
                List.of(
                        "serve",
                        "--hostname", hostname,
                        "--port", String.valueOf(port),
                        "--print-logs",
                        "--log-level", "WARN"
                ),
                isWindows()
        )).directory(cwd.toFile());

        pb.environment().put("PATH", System.getenv("PATH"));
        OpenCodeTextEncoding.configureUtf8Environment(pb.environment(), isWindows());
        pb.redirectErrorStream(true);

        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start opencode serve: " + e.getMessage(), e);
        }

        // Drain stdout/stderr in background to prevent buffer deadlock
        Thread drainThread = new Thread(() -> {
            try (var is = process.getInputStream()) {
                byte[] buf = new byte[4096];
                while (is.read(buf) != -1) {
                    // discard — logged by opencode itself via --print-logs
                }
            } catch (IOException ignored) {
            }
        }, "opencode-serve-drain");
        drainThread.setDaemon(true);
        drainThread.start();

        // Handle unexpected exit
        process.onExit().thenAccept(p -> {
            if (started && this.process == p) {
                log.warn("opencode serve exited with code {}", p.exitValue());
                started = false;
            }
        });

        waitForReady(cwd);
        started = true;
        log.info("opencode serve is ready at {}", baseUrl);
    }

    /**
     * Probes whether opencode serve is already reachable at the configured baseUrl.
     */
    private boolean probeReady() {
        return probeReady(resolveWorkingDirectory());
    }

    private boolean probeReady(Path requiredWorkingDirectory) {
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(OpenCodeEndpoint.uri(baseUrl, "/path"))
                    .header("x-opencode-directory", cwd.toString())
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForReady(Path requiredWorkingDirectory) {
        Path cwd = normalizeWorkingDirectory(requiredWorkingDirectory);
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        String lastError = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(OpenCodeEndpoint.uri(baseUrl, "/path"))
                        .header("x-opencode-directory", cwd.toString())
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
                lastError = "HTTP " + response.statusCode();
            } catch (Exception e) {
                lastError = e.getMessage();
            }
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "opencode serve process exited before becoming ready. Check if port " + port + " is available.");
            }
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for opencode serve to start", e);
            }
        }
        shutdown();
        throw new IllegalStateException("opencode serve did not become ready within timeout: " + lastError);
    }

    private Path normalizeWorkingDirectory(Path requiredWorkingDirectory) {
        return requiredWorkingDirectory == null
                ? resolveWorkingDirectory()
                : requiredWorkingDirectory.toAbsolutePath().normalize();
    }

    private String resolveCommand() {
        return resolveCommand(command, isWindows(), System.getenv("PATH"), System.getenv("PATHEXT"));
    }

    static String resolveCommand(String command, boolean windows, String pathEnv, String pathExtEnv) {
        if (command == null || command.isBlank()) {
            return null;
        }

        if (hasPathSeparator(command)) {
            return firstRunnableCandidate(commandCandidates(Path.of(command), windows, pathExtEnv), windows);
        }

        String separator = windows ? ";" : File.pathSeparator;
        for (String rawDir : splitPath(pathEnv, separator)) {
            if (rawDir == null || rawDir.isBlank()) {
                continue;
            }
            String resolved = firstRunnableCandidate(
                    commandCandidates(Path.of(rawDir).resolve(command), windows, pathExtEnv), windows);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    static List<String> buildCommandLine(String resolvedCommand, List<String> args, boolean windows) {
        List<String> commandLine = new ArrayList<>();
        if (windows && isWindowsCommandShim(resolvedCommand)) {
            commandLine.add("cmd.exe");
            commandLine.add("/d");
            commandLine.add("/s");
            commandLine.add("/c");
            commandLine.add(buildWindowsUtf8Command(resolvedCommand, args));
            return commandLine;
        }
        commandLine.add(resolvedCommand);
        commandLine.addAll(args);
        return commandLine;
    }

    private static String buildWindowsUtf8Command(String resolvedCommand, List<String> args) {
        List<String> parts = new ArrayList<>();
        parts.add(quoteForCmd(resolvedCommand));
        for (String arg : args) {
            parts.add(quoteForCmd(arg));
        }
        return "chcp 65001 >NUL && " + String.join(" ", parts);
    }

    private static String quoteForCmd(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static List<Path> commandCandidates(Path base, boolean windows, String pathExtEnv) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(base);
        if (!windows || hasWindowsExecutableExtension(base)) {
            return candidates;
        }

        for (String extension : windowsPathExtensions(pathExtEnv)) {
            candidates.add(Path.of(base.toString() + extension));
        }
        return candidates;
    }

    private static String firstRunnableCandidate(List<Path> candidates, boolean windows) {
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            if (windows || Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static List<String> splitPath(String pathEnv, String separator) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return List.of();
        }
        return Arrays.asList(pathEnv.split(java.util.regex.Pattern.quote(separator)));
    }

    private static List<String> windowsPathExtensions(String pathExtEnv) {
        String value = pathExtEnv == null || pathExtEnv.isBlank()
                ? ".COM;.EXE;.BAT;.CMD"
                : pathExtEnv;
        return Arrays.stream(value.split(";"))
                .filter(ext -> ext != null && !ext.isBlank())
                .map(ext -> ext.startsWith(".") ? ext : "." + ext)
                .toList();
    }

    private static boolean hasPathSeparator(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private static boolean hasWindowsExecutableExtension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".exe") || lower.endsWith(".cmd")
                || lower.endsWith(".bat") || lower.endsWith(".com");
    }

    private static boolean isWindowsCommandShim(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.endsWith(".cmd") || lower.endsWith(".bat");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostname, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isAlive() {
        return process != null && process.isAlive();
    }
}
