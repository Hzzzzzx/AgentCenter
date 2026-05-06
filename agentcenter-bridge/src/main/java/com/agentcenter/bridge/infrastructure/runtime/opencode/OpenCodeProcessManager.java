package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
            @Value("${agentcenter.runtime.opencode.serve.working-directory:${user.dir}}") String workingDirectory,
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
        if (!enabled) {
            throw new IllegalStateException("OpenCode serve adapter is disabled in configuration");
        }
        if (started && isAlive()) {
            return baseUrl;
        }
        startLock.lock();
        try {
            if (started && isAlive()) {
                return baseUrl;
            }
            startProcess();
            return baseUrl;
        } finally {
            startLock.unlock();
        }
    }

    /**
     * Checks if the opencode serve process is alive and healthy.
     */
    public boolean isHealthy() {
        if (!started || !isAlive()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/path"))
                    .header("x-opencode-directory", resolveWorkingDirectory().toString())
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
        Path configured = Path.of(workingDirectory).toAbsolutePath().normalize();
        if (Files.isDirectory(configured)) {
            return configured;
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
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

    private void startProcess() {
        baseUrl = "http://" + hostname + ":" + port;
        Path cwd = resolveWorkingDirectory();

        // First, check if opencode serve is already running on the target port.
        // This avoids spawning a conflicting process when one is already active.
        if (probeReady()) {
            log.info("opencode serve already running at {}, reusing existing instance", baseUrl);
            started = true;
            return;
        }

        String resolvedCommand = resolveCommand();
        if (resolvedCommand == null) {
            throw new IllegalStateException(
                    "Cannot find opencode executable. Install opencode or configure agentcenter.runtime.opencode.serve.command");
        }

        log.info("Starting opencode serve: {} serve --hostname {} --port {} --print-logs --log-level WARN (cwd={})",
                resolvedCommand, hostname, port, cwd);

        ProcessBuilder pb = new ProcessBuilder(
                resolvedCommand, "serve",
                "--hostname", hostname,
                "--port", String.valueOf(port),
                "--print-logs",
                "--log-level", "WARN"
        ).directory(cwd.toFile());

        pb.environment().put("PATH", System.getenv("PATH"));
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

        waitForReady();
        started = true;
        log.info("opencode serve is ready at {}", baseUrl);
    }

    /**
     * Probes whether opencode serve is already reachable at the configured baseUrl.
     */
    private boolean probeReady() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/path"))
                    .header("x-opencode-directory", resolveWorkingDirectory().toString())
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForReady() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        String lastError = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/path"))
                        .header("x-opencode-directory", resolveWorkingDirectory().toString())
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

    private String resolveCommand() {
        // If the configured command contains a path separator, check existence directly
        if (command.contains("/")) {
            return Files.exists(Path.of(command)) ? command : null;
        }
        // Try running the command to see if it's on PATH
        try {
            Process probe = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = probe.waitFor(5, TimeUnit.SECONDS);
            if (finished) {
                return command;
            }
            probe.destroyForcibly();
        } catch (IOException | InterruptedException e) {
            // not found
        }
        return null;
    }

    private boolean isAlive() {
        return process != null && process.isAlive();
    }
}
