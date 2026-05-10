package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for resolving the runtime workspace directory.
 *
 * <p>Always resolves to {@code <project-root>/runtime-workspace}, where
 * project-root is the directory containing the {@code agentcenter-bridge/} subdirectory.
 * This works regardless of whether the JVM was started from the project root
 * or from inside {@code agentcenter-bridge/}.</p>
 *
 * <p>If the {@code AGENTCENTER_RUNTIME_WORKSPACE} environment variable is set,
 * it takes precedence and is used as-is.</p>
 */
public final class RuntimeWorkspace {

    private static final Logger log = LoggerFactory.getLogger(RuntimeWorkspace.class);

    private RuntimeWorkspace() {}

    /**
     * Resolves and returns the runtime workspace directory, creating it if necessary.
     *
     * @param configuredValue the value from {@code application.yml}
     *                        (may contain Spring placeholders, already resolved by Spring)
     * @return absolute path to the workspace directory
     * @throws IllegalStateException if the directory cannot be created
     */
    public static Path resolve(String configuredValue) {
        Path workspace = resolveProjectWorkspace(configuredValue);

        if (Files.isDirectory(workspace)) {
            return workspace;
        }

        try {
            Files.createDirectories(workspace);
            log.info("Created runtime workspace: {}", workspace);
            return workspace;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create runtime workspace directory: " + workspace + ". "
                            + "Set agentcenter.runtime.opencode.serve.working-directory to a writable path, "
                            + "or export AGENTCENTER_RUNTIME_WORKSPACE.", e);
        }
    }

    /**
     * Returns the workspace path without creating it.
     */
    public static Path resolveWithoutCreate(String configuredValue) {
        return resolveProjectWorkspace(configuredValue);
    }

    /**
     * Finds the project root by walking up from user.dir until a directory
     * containing "agentcenter-bridge" subdirectory is found.
     * Then appends "runtime-workspace".
     */
    private static Path resolveProjectWorkspace(String configuredValue) {
        Path configured = Path.of(configuredValue).toAbsolutePath().normalize();

        if (System.getenv("AGENTCENTER_RUNTIME_WORKSPACE") != null) {
            return configured;
        }

        if (Files.isDirectory(configured)) {
            return configured;
        }

        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("agentcenter-bridge"))) {
                return dir.resolve("runtime-workspace").toAbsolutePath().normalize();
            }
            dir = dir.getParent();
        }

        return configured;
    }
}
