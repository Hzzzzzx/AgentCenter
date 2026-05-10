package com.agentcenter.bridge.application;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.infrastructure.runtime.opencode.RuntimeWorkspace;

/**
 * Resolves the filesystem workspace used by the runtime for a project.
 *
 * M1 keeps the mapping configuration-based. Later this can read a project table
 * without changing Skill/MCP callers again.
 */
@Component
public class ProjectRuntimeWorkspaceResolver {

    private final Environment environment;
    private final String defaultWorkingDirectory;

    public ProjectRuntimeWorkspaceResolver(
            Environment environment,
            @Value("${agentcenter.runtime.opencode.serve.working-directory:${user.dir}/runtime-workspace}") String defaultWorkingDirectory) {
        this.environment = environment;
        this.defaultWorkingDirectory = defaultWorkingDirectory;
    }

    public Path resolve(String projectId) {
        String configured = null;
        if (projectId != null && !projectId.isBlank()) {
            configured = environment.getProperty("agentcenter.runtime.projects." + projectId + ".working-directory");
        }
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty("agentcenter.runtime.project-workdir");
        }
        if (configured == null || configured.isBlank()) {
            return RuntimeWorkspace.resolve(defaultWorkingDirectory);
        }

        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return path;
        }
        return RuntimeWorkspace.resolve(defaultWorkingDirectory);
    }
}
