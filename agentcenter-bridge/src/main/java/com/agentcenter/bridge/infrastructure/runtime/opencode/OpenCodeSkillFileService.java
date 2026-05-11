package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;

/**
 * Encapsulates ALL .opencode/skills file I/O.
 * Moved from the application layer (RuntimeResourceService, SkillRegistryService)
 * so that .opencode path conventions live only in the infrastructure layer.
 */
@Component
public class OpenCodeSkillFileService {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeSkillFileService.class);
    private static final String FRONTMATTER_FIELD = "(?m)^%s\\s*:\\s*[\"']?([^\"'\\n]+)[\"']?\\s*$";
    private static final Pattern SAFE_SKILL_NAME = Pattern.compile("[a-zA-Z0-9._-]+");

    private final Path workingDir;

    public OpenCodeSkillFileService(
            @Value("${agentcenter.runtime.opencode.serve.working-directory}") String workingDirectory) {
        this.workingDir = RuntimeWorkspace.resolve(workingDirectory);
    }

    public List<RuntimeSkillDto> scanSkills() {
        return scanSkills(null);
    }

    public List<RuntimeSkillDto> scanSkills(Path projectWorkdir) {
        Path workspace = resolveWorkingDirectory(projectWorkdir);
        Path skillsRoot = skillsRoot(workspace);
        ensureDirectory(skillsRoot);

        try (var stream = Files.list(skillsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(skillDir -> readSkill(workspace, skillDir))
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(RuntimeSkillDto::name))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan OpenCode skills: " + skillsRoot, e);
        }
    }

    public String installSkill(String skillName, Path sourceDir) {
        return installSkill(null, skillName, sourceDir);
    }

    public String installSkill(Path projectWorkdir, String skillName, Path sourceDir) {
        validateSkillName(skillName);
        Path skillsRoot = skillsRoot(resolveWorkingDirectory(projectWorkdir));
        Path targetDir = skillsRoot.resolve(skillName).normalize();
        if (targetDir.equals(skillsRoot) || !targetDir.startsWith(skillsRoot)) {
            throw new IllegalStateException("Refusing to install skill outside skills directory: " + targetDir);
        }
        try {
            if (Files.exists(targetDir)) {
                deleteRecursivelyOrThrow(targetDir);
            }
            Files.createDirectories(targetDir);
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                for (Path source : walk.filter(Files::isRegularFile).toList()) {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative).normalize();
                    if (!target.startsWith(targetDir)) {
                        throw new IOException("Refusing to copy file outside target directory: " + relative);
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String relativePath = OpenCodeSkillPaths.installedSkillRelativePath(skillName);
            log.info("Installed skill '{}' to {}", skillName, targetDir);
            return relativePath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to install skill '" + skillName + "' to " + targetDir, e);
        }
    }

    public void deleteSkillFiles(String relativePath, String skillName) {
        deleteSkillFiles(null, relativePath, skillName);
    }

    public void deleteSkillFiles(Path projectWorkdir, String relativePath, String skillName) {
        Path workspace = resolveWorkingDirectory(projectWorkdir);
        Path targetDir;
        if (relativePath != null && !relativePath.isBlank()) {
            targetDir = OpenCodeSkillPaths.resolvePortableRelativePath(workspace, relativePath);
        } else {
            targetDir = skillsRoot(workspace).resolve(skillName).normalize();
        }

        Path skillsRoot = skillsRoot(workspace);
        if (targetDir.equals(skillsRoot) || !targetDir.startsWith(skillsRoot)) {
            throw new IllegalStateException("Refusing to delete path outside skills directory: " + targetDir);
        }

        if (Files.exists(targetDir)) {
            try {
                deleteRecursivelyOrThrow(targetDir);
                log.info("Deleted skill files at {}", targetDir);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete skill files at " + targetDir, e);
            }
        }
    }

    public String getSkillsRootPath() {
        return getSkillsRootPath(null);
    }

    public String getSkillsRootPath(Path projectWorkdir) {
        return skillsRoot(resolveWorkingDirectory(projectWorkdir)).toString();
    }

    private Path skillsRoot(Path workingDir) {
        return OpenCodeSkillPaths.managedProjectSkillsRoot(workingDir);
    }

    private List<RuntimeSkillDto> readSkill(Path workspace, Path skillDir) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return List.of();
        }
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String directoryName = skillDir.getFileName().toString();
            String declaredName = firstFrontmatterValue(content, "name", directoryName);
            String name = SAFE_SKILL_NAME.matcher(declaredName).matches() ? declaredName : directoryName;
            String description = firstFrontmatterValue(content, "description", "");
            String checksum = checksumDirectory(skillDir);
            OffsetDateTime updatedAt = OffsetDateTime.ofInstant(
                    Files.getLastModifiedTime(skillFile).toInstant(),
                    ZoneId.systemDefault()
            );
            return List.of(new RuntimeSkillDto(
                    name,
                    description,
                    OpenCodeSkillPaths.portableRelativePath(workspace, skillDir),
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

    private String checksumDirectory(Path dir) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                    digest.update(Files.readAllBytes(path));
                }
            }
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create runtime directory: " + directory, e);
        }
    }

    private void validateSkillName(String skillName) {
        if (skillName == null || skillName.isBlank() || ".".equals(skillName) || "..".equals(skillName)) {
            throw new IllegalArgumentException("Skill name must not be blank, '.', or '..'");
        }
        if (!SAFE_SKILL_NAME.matcher(skillName).matches()) {
            throw new IllegalArgumentException(
                    "Skill name contains unsafe characters (allowed: alphanumeric, dot, hyphen, underscore): " + skillName);
        }
    }

    private Path resolveWorkingDirectory(Path projectWorkdir) {
        if (projectWorkdir != null) {
            Path normalized = projectWorkdir.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return workingDir;
    }

    private void deleteRecursivelyOrThrow(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

}
