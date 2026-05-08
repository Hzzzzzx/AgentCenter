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

    private final Path workingDir;

    public OpenCodeSkillFileService(
            @Value("${agentcenter.runtime.opencode.serve.working-directory}") String workingDirectory) {
        this.workingDir = Path.of(workingDirectory).toAbsolutePath().normalize();
    }

    public List<RuntimeSkillDto> scanSkills() {
        Path skillsRoot = skillsRoot();
        ensureDirectory(skillsRoot);

        try (var stream = Files.list(skillsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(this::readSkill)
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(RuntimeSkillDto::name))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan OpenCode skills: " + skillsRoot, e);
        }
    }

    public String installSkill(String skillName, Path sourceDir) {
        Path targetDir = skillsRoot().resolve(skillName);
        try {
            if (Files.exists(targetDir)) {
                deleteRecursively(targetDir);
            }
            Files.createDirectories(targetDir);
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                for (Path source : walk.filter(Files::isRegularFile).toList()) {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String relativePath = ".opencode/skills/" + skillName;
            log.info("Installed skill '{}' to {}", skillName, targetDir);
            return relativePath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to install skill '" + skillName + "' to " + targetDir, e);
        }
    }

    public void deleteSkillFiles(String relativePath, String skillName) {
        Path targetDir;
        if (relativePath != null && !relativePath.isBlank()) {
            targetDir = workingDir.resolve(relativePath).normalize();
        } else {
            targetDir = skillsRoot().resolve(skillName).normalize();
        }

        Path skillsRoot = skillsRoot();
        if (!targetDir.startsWith(skillsRoot)) {
            throw new IllegalStateException("Refusing to delete path outside skills directory: " + targetDir);
        }

        if (Files.exists(targetDir)) {
            deleteRecursively(targetDir);
            log.info("Deleted skill files at {}", targetDir);
        }
    }

    public String getSkillsRootPath() {
        return skillsRoot().toString();
    }

    private Path skillsRoot() {
        return workingDir.resolve(".opencode").resolve("skills").normalize();
    }

    private List<RuntimeSkillDto> readSkill(Path skillDir) {
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
                    workingDir.relativize(skillDir).toString(),
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

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create runtime directory: " + directory, e);
        }
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
