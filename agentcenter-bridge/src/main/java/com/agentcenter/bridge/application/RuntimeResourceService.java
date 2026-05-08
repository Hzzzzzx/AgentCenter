package com.agentcenter.bridge.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

@Service
public class RuntimeResourceService {

    private final RuntimeGateway runtimeGateway;
    private final String configuredProjectRoot;
    private final AtomicReference<RuntimeSkillSnapshot> skillSnapshot =
            new AtomicReference<>(new RuntimeSkillSnapshot(null, null, List.of()));

    public RuntimeResourceService(RuntimeGateway runtimeGateway,
                                  @Value("${agentcenter.runtime.project-root:}") String configuredProjectRoot) {
        this.runtimeGateway = runtimeGateway;
        this.configuredProjectRoot = configuredProjectRoot;
    }

    public RuntimeSkillRefreshResponse listSkills() {
        RuntimeSkillSnapshot snapshot = skillSnapshot.get();
        if (snapshot.refreshedAt() == null) {
            return refreshSkills();
        }
        String projectRootStr = resolveProjectRoot().toString();
        String skillsPath = runtimeGateway.getSkillsRootPath(RuntimeType.OPENCODE);
        return toResponse(snapshot.refreshedAt(), projectRootStr, skillsPath, snapshot.skills());
    }

    public RuntimeSkillRefreshResponse refreshSkills() {
        List<RuntimeSkillDto> skills = runtimeGateway.scanSkills(RuntimeType.OPENCODE);
        OffsetDateTime refreshedAt = OffsetDateTime.now();
        String projectRootStr = resolveProjectRoot().toString();
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(
                refreshedAt,
                projectRootStr,
                skills
        );
        skillSnapshot.set(snapshot);
        runtimeGateway.refreshSkills(RuntimeType.OPENCODE, snapshot);
        String skillsPath = runtimeGateway.getSkillsRootPath(RuntimeType.OPENCODE);
        return toResponse(refreshedAt, projectRootStr, skillsPath, skills);
    }

    public RuntimeSkillSnapshot currentSkillSnapshot() {
        return skillSnapshot.get();
    }

    private RuntimeSkillRefreshResponse toResponse(OffsetDateTime refreshedAt,
                                                    String projectRoot,
                                                    String skillsPath,
                                                    List<RuntimeSkillDto> skills) {
        return new RuntimeSkillRefreshResponse(
                refreshedAt,
                projectRoot,
                skillsPath,
                skills.size(),
                skills
        );
    }

    private Path resolveProjectRoot() {
        if (configuredProjectRoot != null && !configuredProjectRoot.isBlank()) {
            return Path.of(configuredProjectRoot).toAbsolutePath().normalize();
        }
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "agentcenter-bridge".equals(userDir.getFileName().toString())) {
            return userDir.getParent();
        }
        if (Files.isDirectory(userDir.resolve("agentcenter-bridge"))) {
            return userDir;
        }
        return userDir;
    }
}
