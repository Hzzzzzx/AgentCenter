package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.RuntimeSkillDetailDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.api.dto.RuntimeSkillVersionDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillVersionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillVersionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.function.Function;
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
    private final RuntimeGateway runtimeGateway;
    private final RuntimeResourceService runtimeResourceService;
    private final ProjectRuntimeWorkspaceResolver workspaceResolver;

    public SkillRegistryService(RuntimeSkillMapper skillMapper,
                                RuntimeSkillVersionMapper versionMapper,
                                RuntimeResourceAuditService auditService,
                                WorkflowMapper workflowMapper,
                                IdGenerator idGenerator,
                                RuntimeGateway runtimeGateway,
                                RuntimeResourceService runtimeResourceService,
                                ProjectRuntimeWorkspaceResolver workspaceResolver) {
        this.skillMapper = skillMapper;
        this.versionMapper = versionMapper;
        this.auditService = auditService;
        this.workflowMapper = workflowMapper;
        this.idGenerator = idGenerator;
        this.runtimeGateway = runtimeGateway;
        this.runtimeResourceService = runtimeResourceService;
        this.workspaceResolver = workspaceResolver;
    }

    public List<RuntimeSkillDetailDto> listSkills(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        syncSkillsFromFilesystem(resolvedProjectId);
        return skillMapper.findByProjectId(resolvedProjectId).stream()
                .map(this::toDetailDto)
                .toList();
    }

    public List<RuntimeSkillDetailDto> listProjectSkillCatalog(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        List<RuntimeSkillDto> scannedSkills = scanAndSyncProjectSkills(resolvedProjectId);
        java.util.Set<String> scannedNames = scannedSkills.stream()
                .map(RuntimeSkillDto::name)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> scannedPaths = scannedSkills.stream()
                .map(RuntimeSkillDto::relativePath)
                .collect(java.util.stream.Collectors.toSet());
        return skillMapper.findByProjectId(resolvedProjectId).stream()
                .filter(skill -> scannedNames.contains(skill.getName())
                        || scannedPaths.contains(skill.getRelativePath()))
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
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        validateZipFile(file);
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

                Path skillRoot = resolveSkillPackageRoot(tempDir);
                String skillName = extractSkillNameFromSkillMd(skillRoot);
                if (skillName == null || skillName.isBlank()) {
                    skillName = extractSkillNameFromFilename(file.getOriginalFilename());
                }
                validateSkillName(skillName);
                String checksum = computeDirectoryChecksum(skillRoot);
                String skillMdSummary = extractSkillMdSummary(skillRoot);

                RuntimeSkillEntity existing = skillMapper.findByProjectIdAndName(resolvedProjectId, skillName);
                if (existing != null) {
                    if (checksum.equals(existing.getChecksum())) {
                        auditService.recordAudit(resolvedProjectId, "SKILL", existing.getId(), "UPLOAD",
                                "NOOP", "Same checksum, skipped", null, createdBy);
                        return toDetailDto(existing);
                    }
                    return updateExistingSkill(resolvedProjectId, existing, skillRoot, checksum, fileCount,
                            totalSize, skillMdSummary, createdBy);
                }

                return installNewSkill(resolvedProjectId, skillName, skillRoot, checksum, fileCount,
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
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        RuntimeSkillEntity existing = skillMapper.findById(skillId);
        if (existing == null || !existing.getProjectId().equals(resolvedProjectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }
        validateZipFile(file);

        try {
            Path tempDir = Files.createTempDirectory("skill-update-");
            try {
                extractZip(file, tempDir);
                Path skillRoot = resolveSkillPackageRoot(tempDir);
                String nextSkillName = extractSkillNameFromSkillMd(skillRoot);
                if (nextSkillName == null || nextSkillName.isBlank()) {
                    nextSkillName = existing.getName();
                }
                validateSkillName(nextSkillName);
                RuntimeSkillEntity nameOwner = skillMapper.findByProjectIdAndName(resolvedProjectId, nextSkillName);
                if (nameOwner != null && !nameOwner.getId().equals(existing.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Skill name already exists in project: " + nextSkillName);
                }

                String checksum = computeDirectoryChecksum(skillRoot);
                String skillMdSummary = extractSkillMdSummary(skillRoot);
                long totalSize = computeDirectorySize(skillRoot);
                int fileCount = countFiles(skillRoot);
                String previousName = existing.getName();
                String previousRelativePath = existing.getRelativePath();
                String relativePath = installViaGateway(resolvedProjectId, nextSkillName, skillRoot);
                deleteRenamedSkillFiles(resolvedProjectId, previousName, previousRelativePath, nextSkillName, relativePath);

                RuntimeSkillVersionEntity version = createVersion(existing.getId(), checksum, totalSize,
                        fileCount, relativePath, skillMdSummary, createdBy);

                existing.setName(nextSkillName);
                existing.setDisplayName(nextSkillName);
                existing.setChecksum(checksum);
                existing.setCurrentVersionId(version.getId());
                existing.setRelativePath(relativePath);
                existing.setValidationStatus("VALID");
                existing.setValidationMessage(null);
                skillMapper.update(existing);
                renameWorkflowSkillReferences(resolvedProjectId, previousName, nextSkillName);

                runtimeResourceService.refreshSkills(resolvedProjectId);

                auditService.recordAudit(resolvedProjectId, "SKILL", skillId, "UPDATE_ZIP",
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
        return setSkillStatus(ProjectDefaults.resolveProjectId(projectId), skillId, "ENABLED", "ENABLE", createdBy);
    }

    public RuntimeSkillDetailDto disableSkill(String projectId, String skillId, String createdBy) {
        return setSkillStatus(ProjectDefaults.resolveProjectId(projectId), skillId, "DISABLED", "DISABLE", createdBy);
    }

    public void deleteSkill(String projectId, String skillId, String createdBy) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        RuntimeSkillEntity existing = skillMapper.findById(skillId);
        if (existing == null || !existing.getProjectId().equals(resolvedProjectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + skillId);
        }

        int refCount = workflowMapper.countNodeDefinitionsByProjectIdAndSkillName(resolvedProjectId, existing.getName());
        if (refCount > 0) {
            auditService.recordAudit(resolvedProjectId, "SKILL", skillId, "DELETE",
                    "REJECTED", "Skill referenced by " + refCount + " workflow node(s)", null, createdBy);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete skill: referenced by " + refCount + " workflow node definition(s)");
        }

        try {
            runtimeGateway.deleteSkillFiles(RuntimeType.OPENCODE, workspaceResolver.resolve(resolvedProjectId),
                    existing.getRelativePath(), existing.getName());
        } catch (Exception e) {
            auditService.recordAudit(resolvedProjectId, "SKILL", skillId, "DELETE",
                    "FAILED", "Failed to delete installed skill files: " + e.getMessage(), null, createdBy);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete installed skill files: " + e.getMessage(), e);
        }

        skillMapper.deleteById(skillId);
        runtimeResourceService.refreshSkills(resolvedProjectId);
        auditService.recordAudit(resolvedProjectId, "SKILL", skillId, "DELETE",
                "SUCCESS", "Deleted skill", null, createdBy);
    }

    public RuntimeSkillRefreshResponse refreshSkills(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        RuntimeSkillRefreshResponse response = runtimeResourceService.refreshSkills(resolvedProjectId);
        scanAndSyncSkillsToDb(resolvedProjectId, response.skills());
        return response;
    }

    public synchronized void syncSkillsFromFilesystem(String projectId) {
        scanAndSyncProjectSkills(ProjectDefaults.resolveProjectId(projectId));
    }

    public synchronized List<RuntimeSkillDto> scanAndSyncProjectSkills(String projectId) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        List<RuntimeSkillDto> scannedSkills = runtimeGateway.scanSkills(
                RuntimeType.OPENCODE, workspaceResolver.resolve(resolvedProjectId));
        scanAndSyncSkillsToDb(resolvedProjectId, scannedSkills);
        return scannedSkills;
    }

    public String validateRunnableSkill(String projectId, String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "Workflow node skill is required";
        }
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        try {
            syncSkillsFromFilesystem(resolvedProjectId);
        } catch (Exception e) {
            return "Failed to sync runtime skills for project " + resolvedProjectId + ": " + e.getMessage();
        }
        return validateRegisteredRunnableSkill(resolvedProjectId, skillName);
    }

    public String validateRegisteredRunnableSkill(String projectId, String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "Workflow node skill is required";
        }
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        String requestedSkillName = skillName.trim();
        RuntimeSkillEntity skill = skillMapper.findByProjectIdAndName(resolvedProjectId, requestedSkillName);
        if (skill == null) {
            String suggestion = closestSkillSuggestion(resolvedProjectId, requestedSkillName);
            return "Skill is not registered for project " + resolvedProjectId + ": " + requestedSkillName
                    + ". Use the exact project Skill name." + suggestion;
        }
        if (!"ENABLED".equals(skill.getStatus()) || !"VALID".equals(skill.getValidationStatus())) {
            String status = skill.getStatus() == null ? "UNKNOWN" : skill.getStatus();
            String validation = skill.getValidationStatus() == null ? "UNKNOWN" : skill.getValidationStatus();
            return "Skill is not runnable: " + requestedSkillName + " status=" + status + ", validation=" + validation;
        }
        return null;
    }

    private String closestSkillSuggestion(String projectId, String requestedSkillName) {
        String requestedNormalized = normalizeSkillName(requestedSkillName);
        if (requestedNormalized.isBlank()) {
            return "";
        }
        RuntimeSkillEntity closest = skillMapper.findByProjectId(projectId).stream()
                .filter(skill -> skill.getName() != null && !skill.getName().isBlank())
                .min(java.util.Comparator.comparingInt(skill ->
                        levenshteinDistance(requestedNormalized, normalizeSkillName(skill.getName()))))
                .orElse(null);
        if (closest == null) {
            return "";
        }
        int distance = levenshteinDistance(requestedNormalized, normalizeSkillName(closest.getName()));
        return distance <= 3 ? " Did you mean: " + closest.getName() + "?" : "";
    }

    private String normalizeSkillName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private int levenshteinDistance(String source, String target) {
        int sourceLength = source.length();
        int targetLength = target.length();
        int[][] table = new int[sourceLength + 1][targetLength + 1];
        for (int i = 0; i <= sourceLength; i++) {
            table[i][0] = i;
        }
        for (int j = 0; j <= targetLength; j++) {
            table[0][j] = j;
        }
        for (int i = 1; i <= sourceLength; i++) {
            for (int j = 1; j <= targetLength; j++) {
                int cost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;
                table[i][j] = Math.min(
                        Math.min(table[i - 1][j] + 1, table[i][j - 1] + 1),
                        table[i - 1][j - 1] + cost);
            }
        }
        return table[sourceLength][targetLength];
    }

    public void requireRunnableSkill(String projectId, String skillName) {
        String validationError = validateRunnableSkill(projectId, skillName);
        if (validationError != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationError);
        }
    }

    private void scanAndSyncSkillsToDb(String projectId, List<RuntimeSkillDto> scannedSkills) {
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        int synced = 0;
        List<RuntimeSkillEntity> existingSkills = skillMapper.findByProjectId(projectId);
        Map<String, RuntimeSkillEntity> existingByPath = existingSkills.stream()
                .filter(skill -> skill.getRelativePath() != null && !skill.getRelativePath().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeSkillEntity::getRelativePath,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        Map<String, RuntimeSkillEntity> existingByName = existingSkills.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeSkillEntity::getName,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        Map<String, RuntimeSkillEntity> existingByChecksum = existingSkills.stream()
                .filter(skill -> skill.getChecksum() != null && !skill.getChecksum().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeSkillEntity::getChecksum,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        java.util.Set<String> scannedPaths = new java.util.HashSet<>();
        java.util.Set<String> scannedNames = new java.util.HashSet<>();

        for (RuntimeSkillDto scanned : scannedSkills) {
            scannedPaths.add(scanned.relativePath());
            scannedNames.add(scanned.name());
            RuntimeSkillEntity existing = existingByPath.get(scanned.relativePath());
            if (existing == null) {
                existing = existingByName.get(scanned.name());
            }
            if (existing == null && scanned.checksum() != null && !scanned.checksum().isBlank()) {
                existing = existingByChecksum.get(scanned.checksum());
            }

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
                boolean changed = false;
                boolean wasMissing = "MISSING".equals(existing.getValidationStatus());
                boolean nameConflict = false;
                String previousName = existing.getName();
                if (!Objects.equals(previousName, scanned.name())) {
                    RuntimeSkillEntity nameOwner = existingByName.get(scanned.name());
                    if (nameOwner != null && !nameOwner.getId().equals(existing.getId())) {
                        existing.setValidationStatus("INVALID");
                        existing.setValidationMessage("Skill name conflicts with another project skill: " + scanned.name());
                        nameConflict = true;
                    } else {
                        existing.setName(scanned.name());
                        existing.setDisplayName(scanned.name());
                        renameWorkflowSkillReferences(projectId, previousName, scanned.name());
                    }
                    changed = true;
                }
                if (!Objects.equals(existing.getRelativePath(), scanned.relativePath())) {
                    existing.setRelativePath(scanned.relativePath());
                    changed = true;
                }
                if (!Objects.equals(existing.getChecksum(), scanned.checksum())) {
                    existing.setChecksum(scanned.checksum());
                    changed = true;
                }
                if (!nameConflict && (!"VALID".equals(existing.getValidationStatus()) || existing.getValidationMessage() != null)) {
                    existing.setValidationStatus("VALID");
                    existing.setValidationMessage(null);
                    changed = true;
                }
                if (wasMissing && "DISABLED".equals(existing.getStatus())) {
                    existing.setStatus("ENABLED");
                    changed = true;
                } else if (existing.getStatus() == null || existing.getStatus().isBlank()
                        || "INVALID".equals(existing.getStatus()) || "UPDATING".equals(existing.getStatus())) {
                    existing.setStatus("ENABLED");
                    changed = true;
                }
                if (changed) {
                    existing.setUpdatedAt(now);
                    skillMapper.update(existing);
                    synced++;
                }
            }
        }

        for (RuntimeSkillEntity existing : existingSkills) {
            boolean missingByPath = existing.getRelativePath() != null
                    && !existing.getRelativePath().isBlank()
                    && !scannedPaths.contains(existing.getRelativePath());
            boolean missingByNameWithoutPath = (existing.getRelativePath() == null || existing.getRelativePath().isBlank())
                    && !scannedNames.contains(existing.getName());
            if ((missingByPath || missingByNameWithoutPath) && !"DELETED".equals(existing.getStatus())) {
                existing.setStatus("DISABLED");
                existing.setValidationStatus("MISSING");
                existing.setValidationMessage("Skill files were not found during the latest filesystem refresh");
                existing.setUpdatedAt(now);
                skillMapper.update(existing);
                synced++;
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
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        String relativePath = runtimeGateway.installSkill(RuntimeType.OPENCODE,
                workspaceResolver.resolve(projectId), skillName, tempDir);

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

        runtimeResourceService.refreshSkills(projectId);

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

        String newRelativePath = installViaGateway(projectId, existing.getName(), tempDir);
        RuntimeSkillVersionEntity version = createVersion(existing.getId(), checksum, totalSize,
                fileCount, newRelativePath, skillMdSummary, createdBy);

        existing.setChecksum(checksum);
        existing.setCurrentVersionId(version.getId());
        existing.setValidationStatus("VALID");
        existing.setValidationMessage(null);
        if (!newRelativePath.equals(existing.getRelativePath())) {
            existing.setRelativePath(newRelativePath);
        }
        skillMapper.update(existing);

        runtimeResourceService.refreshSkills(projectId);

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

    private String installViaGateway(String projectId, String skillName, Path sourceDir) {
        return runtimeGateway.installSkill(RuntimeType.OPENCODE,
                workspaceResolver.resolve(projectId), skillName, sourceDir);
    }

    private void renameWorkflowSkillReferences(String projectId, String previousName, String nextName) {
        if (previousName == null || nextName == null || previousName.equals(nextName)) {
            return;
        }
        int definitionCount = workflowMapper.renameSkillReferencesByProjectId(projectId, previousName, nextName);
        int instanceCount = workflowMapper.renameNodeInstanceSkillNameByProjectId(projectId, previousName, nextName);
        if (definitionCount > 0 || instanceCount > 0) {
            log.info("Renamed workflow skill references from '{}' to '{}' (definitions={}, instances={})",
                    previousName, nextName, definitionCount, instanceCount);
        }
    }

    private void deleteRenamedSkillFiles(String projectId,
                                         String previousName,
                                         String previousRelativePath,
                                         String nextName,
                                         String nextRelativePath) {
        if (previousRelativePath == null || previousRelativePath.isBlank()) {
            return;
        }
        if (Objects.equals(previousName, nextName) && Objects.equals(previousRelativePath, nextRelativePath)) {
            return;
        }
        try {
            runtimeGateway.deleteSkillFiles(RuntimeType.OPENCODE, workspaceResolver.resolve(projectId),
                    previousRelativePath, previousName);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete previous skill files after rename: " + e.getMessage(), e);
        }
    }

    private Path resolveSkillPackageRoot(Path extractedDir) throws IOException {
        if (Files.isRegularFile(extractedDir.resolve("SKILL.md"))) {
            return extractedDir;
        }

        try (Stream<Path> children = Files.list(extractedDir)) {
            List<Path> directories = children
                    .filter(Files::isDirectory)
                    .toList();
            if (directories.size() == 1 && Files.isRegularFile(directories.get(0).resolve("SKILL.md"))) {
                return directories.get(0);
            }
        }

        try (Stream<Path> walk = Files.walk(extractedDir, 3)) {
            List<Path> skillFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> "SKILL.md".equals(path.getFileName().toString()))
                    .toList();
            if (skillFiles.size() == 1) {
                return skillFiles.get(0).getParent();
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ZIP must contain exactly one skill root with SKILL.md");
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

    private String extractSkillNameFromSkillMd(Path dir) throws IOException {
        Path skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return null;
        }
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^name\\s*:\\s*[\"']?([^\"'\\n]+)[\"']?\\s*$")
                .matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void validateSkillName(String skillName) {
        if (skillName == null || skillName.isBlank() || ".".equals(skillName) || "..".equals(skillName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill name must not be blank, '.', or '..'");
        }
        if (!skillName.matches("[a-zA-Z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Skill name contains unsafe characters: " + skillName);
        }
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
