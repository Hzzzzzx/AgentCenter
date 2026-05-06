package com.agentcenter.bridge.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;

@Service
public class RuntimeResourceService {

    private static final String FRONTMATTER_FIELD = "(?m)^%s\\s*:\\s*[\"']?([^\"'\\n]+)[\"']?\\s*$";

    private final AgentRuntimeAdapter runtimeAdapter;
    private final String configuredProjectRoot;
    private final AtomicReference<RuntimeSkillSnapshot> skillSnapshot =
            new AtomicReference<>(new RuntimeSkillSnapshot(null, null, List.of()));

    public RuntimeResourceService(AgentRuntimeAdapter runtimeAdapter,
                                  @Value("${agentcenter.runtime.project-root:}") String configuredProjectRoot) {
        this.runtimeAdapter = runtimeAdapter;
        this.configuredProjectRoot = configuredProjectRoot;
    }

    public RuntimeSkillRefreshResponse listSkills() {
        RuntimeSkillSnapshot snapshot = skillSnapshot.get();
        if (snapshot.refreshedAt() == null) {
            return refreshSkills();
        }
        Path projectRoot = resolveProjectRoot();
        return toResponse(snapshot.refreshedAt(), projectRoot, snapshot.skills());
    }

    public RuntimeSkillRefreshResponse refreshSkills() {
        Path projectRoot = resolveProjectRoot();
        Path skillsRoot = projectRoot.resolve(".opencode").resolve("skills").normalize();
        ensureDirectory(skillsRoot);

        List<RuntimeSkillDto> skills = scanSkillDirectories(projectRoot, skillsRoot);
        OffsetDateTime refreshedAt = OffsetDateTime.now();
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(
                refreshedAt,
                projectRoot.toString(),
                skills
        );
        skillSnapshot.set(snapshot);
        runtimeAdapter.refreshSkills(snapshot);
        return toResponse(refreshedAt, projectRoot, skills);
    }

    public RuntimeSkillSnapshot currentSkillSnapshot() {
        return skillSnapshot.get();
    }

    private List<RuntimeSkillDto> scanSkillDirectories(Path projectRoot, Path skillsRoot) {
        try (var stream = Files.list(skillsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> readSkill(projectRoot, path))
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(RuntimeSkillDto::name))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan OpenCode skills: " + skillsRoot, e);
        }
    }

    private List<RuntimeSkillDto> readSkill(Path projectRoot, Path skillDir) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return List.of();
        }
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String directoryName = skillDir.getFileName().toString();
            String name = firstFrontmatterValue(content, "name", directoryName);
            String description = firstFrontmatterValue(content, "description", "");
            String checksum = sha256(content);
            OffsetDateTime updatedAt = OffsetDateTime.ofInstant(
                    Files.getLastModifiedTime(skillFile).toInstant(),
                    ZoneId.systemDefault()
            );
            return List.of(new RuntimeSkillDto(
                    name,
                    description,
                    projectRoot.relativize(skillDir).toString(),
                    checksum,
                    updatedAt
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill file: " + skillFile, e);
        }
    }

    private String firstFrontmatterValue(String content, String field, String fallback) {
        Matcher matcher = Pattern.compile(FRONTMATTER_FIELD.formatted(Pattern.quote(field))).matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private RuntimeSkillRefreshResponse toResponse(OffsetDateTime refreshedAt,
                                                   Path projectRoot,
                                                   List<RuntimeSkillDto> skills) {
        Path skillsRoot = projectRoot.resolve(".opencode").resolve("skills").normalize();
        return new RuntimeSkillRefreshResponse(
                refreshedAt,
                projectRoot.toString(),
                skillsRoot.toString(),
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

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create runtime directory: " + directory, e);
        }
    }
}
