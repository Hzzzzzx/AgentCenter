package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.RuntimeSkillDetailDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.api.dto.RuntimeSkillVersionDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillVersionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillVersionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistryService.class);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_ZIP_SIZE = 10 * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 100;

    private final RuntimeSkillMapper skillMapper;
    private final RuntimeSkillVersionMapper versionMapper;
    private final RuntimeResourceAuditService auditService;
    private final WorkflowMapper workflowMapper;
    private final IdGenerator idGenerator;
    private final String workingDirectory;
    private final RuntimeResourceService runtimeResourceService;

    public SkillRegistryService(RuntimeSkillMapper skillMapper,
                                RuntimeSkillVersionMapper versionMapper,
                                RuntimeResourceAuditService auditService,
                                WorkflowMapper workflowMapper,
                                IdGenerator idGenerator,
                                @Value("${agentcenter.runtime.opencode.serve.working-directory}") String workingDirectory,
                                RuntimeResourceService runtimeResourceService) {
        this.skillMapper = skillMapper;
        this.versionMapper = versionMapper;
        this.auditService = auditService;
        this.workflowMapper = workflowMapper;
        this.idGenerator = idGenerator;
        this.workingDirectory = workingDirectory;
        this.runtimeResourceService = runtimeResourceService;
    }

    public List<RuntimeSkillDetailDto> listSkills(String projectId) {
        return skillMapper.findByProjectId(projectId).stream()
                .map(this::toDetailDto)
                .toList();
    }

    public RuntimeSkillDetailDto getSkill(String projectId, String skillId) {
        RuntimeSkillEntity entity = skillMapper.findById(skillId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }
        return toDetailDto(entity);
    }

    public RuntimeSkillDetailDto uploadSkill(String projectId, MultipartFile file, String createdBy) {
        validateZipFile(file);
        String skillName = extractSkillNameFromFilename(file.getOriginalFilename());

        try {
            Path tempDir = Files.createTempDirectory("skill-upload-");
            try {
                ZipInputStream zis = new ZipInputStream(file.getInputStream());
                ZipEntry entry;
                int fileCount = 0;
                boolean hasSkillMd = false;
                long totalSize = 0;

                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.contains("..")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains path traversal: " + entryName);
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(tempDir.resolve(entryName));
                        continue;
                    }
                    fileCount++;
                    if (fileCount > MAX_FILE_COUNT) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP exceeds max file count: " + MAX_FILE_COUNT);
                    }
                    Path targetPath = tempDir.resolve(entryName).normalize();
                    if (!targetPath.startsWith(tempDir)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP entry escapes target directory: " + entryName);
                    }
                    Files.createDirectories(targetPath.getParent());
                    byte[] content = zis.readAllBytes();
                    totalSize += content.length;
                    Files.write(targetPath, content);
                    if (entryName.endsWith("SKILL.md") || Path.of(entryName).getFileName().toString().equals("SKILL.md")) {
                        hasSkillMd = true;
                    }
                    zis.closeEntry();
                }

                if (!hasSkillMd) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP must contain a SKILL.md file");
                }

                String checksum = computeDirectoryChecksum(tempDir);
                String skillMdSummary = extractSkillMdSummary(tempDir);

                RuntimeSkillEntity existing = skillMapper.findByProjectIdAndName(projectId, skillName);
                if (existing != null) {
                    if (checksum.equals(existing.getChecksum())) {
                        auditService.recordAudit(projectId, "SKILL", existing.getId(), "UPLOAD",
                                "NOOP", "Same checksum, skipped", null, createdBy);
                        return toDetailDto(existing);
                    }
                    return updateExistingSkill(projectId, existing, tempDir, checksum, fileCount,
                            totalSize, skillMdSummary, createdBy);
                }

                return installNewSkill(projectId, skillName, tempDir, checksum, fileCount,
                        totalSize, skillMdSummary, createdBy);
            } finally {
                deleteRecursively(tempDir);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process ZIP: " + e.getMessage(), e);
        }
    }

    public RuntimeSkillDetailDto updateSkillZip(String projectId, String skillId,
                                                 MultipartFile file, String createdBy) {
        RuntimeSkillEntity existing = skillMapper.findById(skillId);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }
        validateZipFile(file);

        try {
            Path tempDir = Files.createTempDirectory("skill-update-");
            try {
                extractZip(file, tempDir);
                String checksum = computeDirectoryChecksum(tempDir);
                String skillMdSummary = extractSkillMdSummary(tempDir);
                long totalSize = computeDirectorySize(tempDir);
                int fileCount = countFiles(tempDir);

                RuntimeSkillVersionEntity version = createVersion(existing.getId(), checksum, totalSize,
                        fileCount, existing.getRelativePath(), skillMdSummary, createdBy);

                existing.setChecksum(checksum);
                existing.setCurrentVersionId(version.getId());
                existing.setValidationStatus("VALID");
                existing.setValidationMessage(null);
                skillMapper.update(existing);

                installToSkillDirectory(existing.getName(), tempDir);

                auditService.recordAudit(projectId, "SKILL", skillId, "UPDATE_ZIP",
                        "SUCCESS", "Updated skill ZIP", null, createdBy);
                return toDetailDto(existing);
            } finally {
                deleteRecursively(tempDir);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process ZIP: " + e.getMessage(), e);
        }
    }

    public RuntimeSkillDetailDto enableSkill(String projectId, String skillId, String createdBy) {
        return setSkillStatus(projectId, skillId, "ENABLED", "ENABLE", createdBy);
    }

    public RuntimeSkillDetailDto disableSkill(String projectId, String skillId, String createdBy) {
        return setSkillStatus(projectId, skillId, "DISABLED", "DISABLE", createdBy);
    }

    public void deleteSkill(String projectId, String skillId, String createdBy) {
        RuntimeSkillEntity existing = skillMapper.findById(skillId);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }

        int refCount = workflowMapper.countNodeDefinitionsBySkillName(existing.getName());
        if (refCount > 0) {
            auditService.recordAudit(projectId, "SKILL", skillId, "DELETE",
                    "REJECTED", "Skill referenced by " + refCount + " workflow node(s)", null, createdBy);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete skill: referenced by " + refCount + " workflow node definition(s)");
        }

        skillMapper.deleteById(skillId);
        auditService.recordAudit(projectId, "SKILL", skillId, "DELETE",
                "SUCCESS", "Deleted skill", null, createdBy);
    }

    public RuntimeSkillRefreshResponse refreshSkills(String projectId) {
        RuntimeSkillRefreshResponse response = runtimeResourceService.refreshSkills();
        scanAndSyncSkillsToDb(projectId, response.skills());
        return response;
    }

    private void scanAndSyncSkillsToDb(String projectId, List<RuntimeSkillDto> scannedSkills) {
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        int synced = 0;

        for (RuntimeSkillDto scanned : scannedSkills) {
            RuntimeSkillEntity existing = skillMapper.findByProjectIdAndName(projectId, scanned.name());

            if (existing == null) {
                String id = idGenerator.nextId();

                String description = scanned.description();
                if (description != null && description.length() > 2000) {
                    description = description.substring(0, 2000);
                }

                RuntimeSkillEntity entity = new RuntimeSkillEntity();
                entity.setId(id);
                entity.setProjectId(projectId);
                entity.setName(scanned.name());
                entity.setDisplayName(scanned.name());
                entity.setDescription(description);
                entity.setStatus("ENABLED");
                entity.setSource("LOCAL_SCAN");
                entity.setRelativePath(scanned.relativePath());
                entity.setChecksum(scanned.checksum());
                entity.setValidationStatus("VALID");
                entity.setCreatedBy("system");
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);

                String versionId = idGenerator.nextId();
                RuntimeSkillVersionEntity version = new RuntimeSkillVersionEntity();
                version.setId(versionId);
                version.setSkillId(id);
                version.setVersionNo("0.0.0");
                version.setPackageChecksum(scanned.checksum());
                version.setInstalledRelativePath(scanned.relativePath());
                version.setStatus("ACTIVE");
                version.setCreatedBy("system");
                version.setCreatedAt(now);
                versionMapper.insert(version);

                entity.setCurrentVersionId(versionId);
                skillMapper.insert(entity);
                synced++;
            } else {
                if (!scanned.checksum().equals(existing.getChecksum())) {
                    existing.setChecksum(scanned.checksum());
                    existing.setUpdatedAt(now);
                    existing.setValidationStatus("VALID");
                    skillMapper.update(existing);
                    synced++;
                }
            }
        }

        if (synced > 0) {
            auditService.recordAudit(projectId, "SKILL", "", "REFRESH", "SUCCESS",
                    "Synced " + synced + " skill(s) from filesystem to DB", null, "system");
        }
    }

    public List<RuntimeSkillVersionDto> getSkillVersions(String projectId, String skillId) {
        RuntimeSkillEntity skill = skillMapper.findById(skillId);
        if (skill == null || !skill.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }
        return versionMapper.findBySkillId(skillId).stream()
                .map(this::toVersionDto)
                .toList();
    }

    private RuntimeSkillDetailDto setSkillStatus(String projectId, String skillId,
                                                  String status, String action, String createdBy) {
        RuntimeSkillEntity existing = skillMapper.findById(skillId);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }
        existing.setStatus(status);
        skillMapper.update(existing);
        auditService.recordAudit(projectId, "SKILL", skillId, action,
                "SUCCESS", "Status changed to " + status, null, createdBy);
        return toDetailDto(existing);
    }

    private RuntimeSkillDetailDto installNewSkill(String projectId, String skillName, Path tempDir,
                                                   String checksum, int fileCount, long totalSize,
                                                   String skillMdSummary, String createdBy) throws IOException {
        String id = idGenerator.nextId();
        Path projectRoot = Path.of(workingDirectory).toAbsolutePath().normalize();
        String relativePath = ".opencode/skills/" + skillName;
        Path installPath = projectRoot.resolve(relativePath);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        RuntimeSkillEntity entity = new RuntimeSkillEntity();
        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setName(skillName);
        entity.setDisplayName(skillName);
        entity.setStatus("ENABLED");
        entity.setSource("UPLOAD");
        entity.setRelativePath(relativePath);
        entity.setChecksum(checksum);
        entity.setValidationStatus("VALID");
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        RuntimeSkillVersionEntity version = createVersion(id, checksum, totalSize, fileCount,
                relativePath, skillMdSummary, createdBy);
        entity.setCurrentVersionId(version.getId());

        skillMapper.insert(entity);

        installToSkillDirectory(skillName, tempDir);

        auditService.recordAudit(projectId, "SKILL", id, "UPLOAD",
                "SUCCESS", "Created new skill", null, createdBy);
        return toDetailDto(entity);
    }

    private RuntimeSkillDetailDto updateExistingSkill(String projectId, RuntimeSkillEntity existing,
                                                       Path tempDir, String checksum, int fileCount,
                                                       long totalSize, String skillMdSummary,
                                                       String createdBy) throws IOException {
        RuntimeSkillVersionEntity prevVersion = versionMapper.findActiveBySkillId(existing.getId());
        if (prevVersion != null) {
            versionMapper.updateStatus(prevVersion.getId(), "SUPERSEDED");
        }

        RuntimeSkillVersionEntity version = createVersion(existing.getId(), checksum, totalSize,
                fileCount, existing.getRelativePath(), skillMdSummary, createdBy);

        existing.setChecksum(checksum);
        existing.setCurrentVersionId(version.getId());
        existing.setValidationStatus("VALID");
        existing.setValidationMessage(null);
        skillMapper.update(existing);

        installToSkillDirectory(existing.getName(), tempDir);

        auditService.recordAudit(projectId, "SKILL", existing.getId(), "UPLOAD",
                "SUCCESS", "Updated existing skill (new version)", null, createdBy);
        return toDetailDto(existing);
    }

    private RuntimeSkillVersionEntity createVersion(String skillId, String checksum, long packageSize,
                                                     int fileCount, String relativePath,
                                                     String skillMdSummary, String createdBy) {
        String versionId = idGenerator.nextId();
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        RuntimeSkillVersionEntity version = new RuntimeSkillVersionEntity();
        version.setId(versionId);
        version.setSkillId(skillId);
        version.setVersionNo(incrementVersion());
        version.setPackageChecksum(checksum);
        version.setPackageSize(packageSize);
        version.setFileCount(fileCount);
        version.setInstalledRelativePath(relativePath);
        version.setSkillMdSummary(skillMdSummary);
        version.setStatus("ACTIVE");
        version.setCreatedBy(createdBy);
        version.setCreatedAt(now);

        versionMapper.insert(version);
        return version;
    }

    private void installToSkillDirectory(String skillName, Path sourceDir) throws IOException {
        Path projectRoot = Path.of(workingDirectory).toAbsolutePath().normalize();
        Path targetDir = projectRoot.resolve(".opencode").resolve("skills").resolve(skillName);
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
    }

    private void validateZipFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP file exceeds 10MB limit");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || (!originalName.toLowerCase().endsWith(".zip"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ZIP files are accepted");
        }
    }

    private void extractZip(MultipartFile file, Path targetDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(file.getInputStream());
        ZipEntry entry;
        int fileCount = 0;

        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();
            if (entryName.contains("..")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains path traversal: " + entryName);
            }
            if (entry.isDirectory()) {
                Files.createDirectories(targetDir.resolve(entryName));
                continue;
            }
            fileCount++;
            if (fileCount > MAX_FILE_COUNT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP exceeds max file count: " + MAX_FILE_COUNT);
            }
            Path targetPath = targetDir.resolve(entryName).normalize();
            if (!targetPath.startsWith(targetDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP entry escapes target directory: " + entryName);
            }
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, zis.readAllBytes());
            zis.closeEntry();
        }
    }

    private String extractSkillNameFromFilename(String filename) {
        if (filename == null) return "unnamed-skill";
        String name = filename;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return name.isEmpty() ? "unnamed-skill" : name;
    }

    private String extractSkillMdSummary(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if ("SKILL.md".equals(p.getFileName().toString())) {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    if (content.length() > 500) {
                        return content.substring(0, 500) + "...";
                    }
                    return content;
                }
            }
        }
        return null;
    }

    private String computeDirectoryChecksum(Path dir) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(p -> {
                        try {
                            digest.update(Files.readAllBytes(p));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            throw e;
        }
    }

    private long computeDirectorySize(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0; }
                    })
                    .sum();
        }
    }

    private int countFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile).count();
        }
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private String incrementVersion() {
        return "0.0.0";
    }

    private RuntimeSkillDetailDto toDetailDto(RuntimeSkillEntity e) {
        return new RuntimeSkillDetailDto(
                e.getId(),
                e.getProjectId(),
                e.getName(),
                e.getDisplayName(),
                e.getDescription(),
                e.getCurrentVersionId(),
                e.getStatus(),
                e.getSource(),
                e.getRelativePath(),
                e.getChecksum(),
                e.getValidationStatus(),
                e.getValidationMessage(),
                e.getCreatedBy(),
                parseDateTime(e.getCreatedAt()),
                parseDateTime(e.getUpdatedAt()),
                null,
                null
        );
    }

    private RuntimeSkillVersionDto toVersionDto(RuntimeSkillVersionEntity e) {
        return new RuntimeSkillVersionDto(
                e.getId(),
                e.getSkillId(),
                e.getVersionNo(),
                e.getPackageChecksum(),
                e.getPackageSize(),
                e.getFileCount(),
                e.getInstalledRelativePath(),
                e.getManifestJson(),
                e.getSkillMdSummary(),
                e.getStatus(),
                e.getCreatedBy(),
                parseDateTime(e.getCreatedAt())
        );
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, SQLITE_DATETIME).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return OffsetDateTime.parse(value);
        }
    }
}
