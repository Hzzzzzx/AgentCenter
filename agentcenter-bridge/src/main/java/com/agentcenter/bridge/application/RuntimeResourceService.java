package com.agentcenter.bridge.application;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

@Service
public class RuntimeResourceService {

    private final RuntimeGateway runtimeGateway;
    private final ProjectRuntimeWorkspaceResolver workspaceResolver;
    private final ConcurrentMap<String, RuntimeSkillSnapshot> skillSnapshots = new ConcurrentHashMap<>();

    public RuntimeResourceService(RuntimeGateway runtimeGateway,
                                  ProjectRuntimeWorkspaceResolver workspaceResolver) {
        this.runtimeGateway = runtimeGateway;
        this.workspaceResolver = workspaceResolver;
    }

    public RuntimeSkillRefreshResponse listSkills() {
        return listSkills(null);
    }

    public RuntimeSkillRefreshResponse listSkills(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        RuntimeSkillSnapshot snapshot = skillSnapshots.get(resolvedProjectId);
        if (snapshot == null || snapshot.refreshedAt() == null) {
            return refreshSkills(resolvedProjectId);
        }
        Path projectWorkdir = resolveProjectWorkdir(resolvedProjectId);
        String projectRootStr = snapshot.projectRoot() == null ? projectWorkdir.toString() : snapshot.projectRoot();
        String skillsPath = runtimeGateway.getSkillsRootPath(RuntimeType.OPENCODE, Path.of(projectRootStr));
        return toResponse(snapshot.refreshedAt(), projectRootStr, skillsPath, snapshot.skills());
    }

    public RuntimeSkillRefreshResponse refreshSkills() {
        return refreshSkills(null);
    }

    public RuntimeSkillRefreshResponse refreshSkills(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        Path projectWorkdir = resolveProjectWorkdir(resolvedProjectId);
        List<RuntimeSkillDto> skills = runtimeGateway.scanSkills(RuntimeType.OPENCODE, projectWorkdir);
        OffsetDateTime refreshedAt = OffsetDateTime.now();
        String projectRootStr = projectWorkdir.toString();
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(
                refreshedAt,
                projectRootStr,
                skills
        );
        skillSnapshots.put(resolvedProjectId, snapshot);
        runtimeGateway.refreshSkills(RuntimeType.OPENCODE, snapshot);
        String skillsPath = runtimeGateway.getSkillsRootPath(RuntimeType.OPENCODE, projectWorkdir);
        return toResponse(refreshedAt, projectRootStr, skillsPath, skills);
    }

    public RuntimeSkillSnapshot currentSkillSnapshot() {
        return currentSkillSnapshot(null);
    }

    public RuntimeSkillSnapshot currentSkillSnapshot(String projectId) {
        return skillSnapshots.getOrDefault(
                ProjectDefaults.resolveProjectId(projectId),
                new RuntimeSkillSnapshot(null, null, List.of()));
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

    private Path resolveProjectWorkdir(String projectId) {
        return workspaceResolver.resolve(projectId);
    }
}
