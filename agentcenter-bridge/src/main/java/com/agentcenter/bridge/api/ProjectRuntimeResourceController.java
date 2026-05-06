package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.ProjectMcpServerDto;
import com.agentcenter.bridge.api.dto.ProjectMcpToolSnapshotDto;
import com.agentcenter.bridge.api.dto.RuntimeResourceAuditDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillDetailDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.api.dto.RuntimeSkillVersionDto;
import com.agentcenter.bridge.application.McpRegistryService;
import com.agentcenter.bridge.application.RuntimeResourceAuditService;
import com.agentcenter.bridge.application.SkillRegistryService;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeResourceAuditEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/runtime")
public class ProjectRuntimeResourceController {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SkillRegistryService skillRegistryService;
    private final McpRegistryService mcpRegistryService;
    private final RuntimeResourceAuditService auditService;

    public ProjectRuntimeResourceController(SkillRegistryService skillRegistryService,
                                             McpRegistryService mcpRegistryService,
                                             RuntimeResourceAuditService auditService) {
        this.skillRegistryService = skillRegistryService;
        this.mcpRegistryService = mcpRegistryService;
        this.auditService = auditService;
    }

    @GetMapping("/skills")
    public List<RuntimeSkillDetailDto> listSkills(@PathVariable String projectId) {
        return skillRegistryService.listSkills(projectId);
    }

    @GetMapping("/skills/{skillId}")
    public RuntimeSkillDetailDto getSkill(@PathVariable String projectId,
                                          @PathVariable String skillId) {
        return skillRegistryService.getSkill(projectId, skillId);
    }

    @PostMapping("/skills/upload")
    public ResponseEntity<RuntimeSkillDetailDto> uploadSkill(@PathVariable String projectId,
                                                              @RequestParam("file") MultipartFile file,
                                                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RuntimeSkillDetailDto result = skillRegistryService.uploadSkill(projectId, file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/skills/{skillId}/zip")
    public RuntimeSkillDetailDto updateSkillZip(@PathVariable String projectId,
                                                 @PathVariable String skillId,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return skillRegistryService.updateSkillZip(projectId, skillId, file, userId);
    }

    @PostMapping("/skills/{skillId}/enable")
    public RuntimeSkillDetailDto enableSkill(@PathVariable String projectId,
                                              @PathVariable String skillId,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return skillRegistryService.enableSkill(projectId, skillId, userId);
    }

    @PostMapping("/skills/{skillId}/disable")
    public RuntimeSkillDetailDto disableSkill(@PathVariable String projectId,
                                               @PathVariable String skillId,
                                               @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return skillRegistryService.disableSkill(projectId, skillId, userId);
    }

    @DeleteMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable String projectId,
                            @PathVariable String skillId,
                            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        skillRegistryService.deleteSkill(projectId, skillId, userId);
    }

    @PostMapping("/skills/refresh")
    public RuntimeSkillRefreshResponse refreshSkills(@PathVariable String projectId) {
        return skillRegistryService.refreshSkills(projectId);
    }

    @GetMapping("/skills/{skillId}/audits")
    public List<RuntimeResourceAuditDto> getSkillAudits(@PathVariable String projectId,
                                                         @PathVariable String skillId) {
        return auditService.getAudits(projectId, "SKILL", skillId).stream()
                .map(this::toAuditDto)
                .toList();
    }

    @GetMapping("/skills/{skillId}/versions")
    public List<RuntimeSkillVersionDto> getSkillVersions(@PathVariable String projectId,
                                                           @PathVariable String skillId) {
        return skillRegistryService.getSkillVersions(projectId, skillId);
    }

    @GetMapping("/mcps")
    public ResponseEntity<List<ProjectMcpServerDto>> listMcps(@PathVariable String projectId) {
        return ResponseEntity.ok(mcpRegistryService.listMcps(projectId));
    }

    @GetMapping("/mcps/{mcpId}")
    public ResponseEntity<ProjectMcpServerDto> getMcp(@PathVariable String projectId,
                                                       @PathVariable String mcpId) {
        return ResponseEntity.ok(mcpRegistryService.getMcp(projectId, mcpId));
    }

    @PostMapping("/mcps/import")
    public ResponseEntity<List<ProjectMcpServerDto>> importMcpConfig(@PathVariable String projectId) {
        return ResponseEntity.ok(mcpRegistryService.importMcpConfig(projectId));
    }

    @PostMapping("/mcps/{mcpId}/enable")
    public ResponseEntity<ProjectMcpServerDto> enableMcp(@PathVariable String projectId,
                                                          @PathVariable String mcpId) {
        return ResponseEntity.ok(mcpRegistryService.enableMcp(projectId, mcpId));
    }

    @PostMapping("/mcps/{mcpId}/disable")
    public ResponseEntity<ProjectMcpServerDto> disableMcp(@PathVariable String projectId,
                                                           @PathVariable String mcpId) {
        return ResponseEntity.ok(mcpRegistryService.disableMcp(projectId, mcpId));
    }

    @PostMapping("/mcps/{mcpId}/test")
    public ResponseEntity<ProjectMcpServerDto> testMcp(@PathVariable String projectId,
                                                        @PathVariable String mcpId) {
        return ResponseEntity.ok(mcpRegistryService.testMcpConnection(projectId, mcpId));
    }

    @PostMapping("/mcps/{mcpId}/refresh-tools")
    public ResponseEntity<List<ProjectMcpToolSnapshotDto>> refreshMcpTools(@PathVariable String projectId,
                                                                            @PathVariable String mcpId) {
        return ResponseEntity.ok(mcpRegistryService.refreshMcpTools(projectId, mcpId));
    }

    @PostMapping("/mcps/refresh")
    public ResponseEntity<List<ProjectMcpServerDto>> refreshAllMcps(@PathVariable String projectId) {
        mcpRegistryService.refreshAllMcps(projectId);
        return ResponseEntity.ok(mcpRegistryService.listMcps(projectId));
    }

    @GetMapping("/mcps/{mcpId}/audits")
    public ResponseEntity<List<RuntimeResourceAuditDto>> getMcpAudits(@PathVariable String projectId,
                                                                       @PathVariable String mcpId) {
        var audits = auditService.getAudits(projectId, "MCP", mcpId);
        return ResponseEntity.ok(audits.stream().map(this::toAuditDto).toList());
    }

    private RuntimeResourceAuditDto toAuditDto(RuntimeResourceAuditEntity e) {
        return new RuntimeResourceAuditDto(
                e.getId(),
                e.getProjectId(),
                e.getResourceType(),
                e.getResourceId(),
                e.getAction(),
                e.getStatus(),
                e.getSummary(),
                e.getDetailJson(),
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
