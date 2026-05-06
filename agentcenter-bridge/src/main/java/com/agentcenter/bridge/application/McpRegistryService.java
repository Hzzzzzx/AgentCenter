package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.ProjectMcpServerDto;
import com.agentcenter.bridge.api.dto.ProjectMcpToolSnapshotDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectMcpServerEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectMcpServerMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectMcpToolSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class McpRegistryService {

    private static final Logger log = LoggerFactory.getLogger(McpRegistryService.class);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectMcpServerMapper mcpServerMapper;
    private final ProjectMcpToolSnapshotMapper toolSnapshotMapper;
    private final RuntimeResourceAuditService auditService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final String workingDirectory;

    public McpRegistryService(ProjectMcpServerMapper mcpServerMapper,
                              ProjectMcpToolSnapshotMapper toolSnapshotMapper,
                              RuntimeResourceAuditService auditService,
                              IdGenerator idGenerator,
                              ObjectMapper objectMapper,
                              @Value("${agentcenter.runtime.opencode.serve.working-directory}") String workingDirectory) {
        this.mcpServerMapper = mcpServerMapper;
        this.toolSnapshotMapper = toolSnapshotMapper;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.workingDirectory = workingDirectory;
    }

    public List<ProjectMcpServerDto> listMcps(String projectId) {
        List<ProjectMcpServerEntity> entities = mcpServerMapper.findByProjectId(projectId);
        return entities.stream().map(this::toDto).toList();
    }

    public ProjectMcpServerDto getMcp(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = mcpServerMapper.findById(mcpId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found");
        }
        return toDto(entity);
    }

    public List<ProjectMcpServerDto> importMcpConfig(String projectId) {
        Path configPath = Path.of(workingDirectory).resolve(".opencode").resolve("mcp.json");
        if (!Files.exists(configPath)) {
            configPath = Path.of(workingDirectory).resolve(".opencode").resolve("mcp.agentcenter.json");
        }
        if (!Files.exists(configPath)) {
            auditService.recordAudit(projectId, "MCP", "", "REFRESH", "FAILED",
                    "No MCP config file found", null, null);
            return listMcps(projectId);
        }

        try {
            String content = Files.readString(configPath);
            Map<String, Object> config = objectMapper.readValue(content, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> servers = (Map<String, Object>) config.get("mcpServers");
            if (servers == null) {
                return listMcps(projectId);
            }

            int imported = 0;
            for (Map.Entry<String, Object> entry : servers.entrySet()) {
                String serverName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> serverConfig = (Map<String, Object>) entry.getValue();

                ProjectMcpServerEntity existing = mcpServerMapper.findByProjectIdAndName(projectId, serverName);
                String configJson = objectMapper.writeValueAsString(serverConfig);

                if (existing == null) {
                    ProjectMcpServerEntity entity = new ProjectMcpServerEntity();
                    entity.setId(idGenerator.nextId());
                    entity.setProjectId(projectId);
                    entity.setName(serverName);
                    entity.setServerType(determineServerType(serverConfig));
                    entity.setStatus("DISABLED");
                    entity.setConfigJson(configJson);
                    entity.setConfigChecksum(computeChecksum(configJson));
                    entity.setLastHealthStatus("UNKNOWN");
                    String now = LocalDateTime.now().format(SQLITE_DATETIME);
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    mcpServerMapper.insert(entity);
                    imported++;
                } else {
                    existing.setConfigJson(configJson);
                    existing.setConfigChecksum(computeChecksum(configJson));
                    mcpServerMapper.update(existing);
                }
            }
            auditService.recordAudit(projectId, "MCP", "", "REFRESH", "SUCCESS",
                    "Imported " + imported + " MCP servers", null, null);
        } catch (Exception e) {
            log.error("Failed to import MCP config for project {}", projectId, e);
            auditService.recordAudit(projectId, "MCP", "", "REFRESH", "FAILED",
                    "Failed to import MCP config: " + e.getMessage(), null, null);
        }
        return listMcps(projectId);
    }

    public ProjectMcpServerDto enableMcp(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = findOrThrow(projectId, mcpId);
        entity.setStatus("ENABLED");
        mcpServerMapper.update(entity);
        auditService.recordAudit(projectId, "MCP", mcpId, "ENABLE", "SUCCESS",
                "MCP " + entity.getName() + " enabled", null, null);
        return toDto(entity);
    }

    public ProjectMcpServerDto disableMcp(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = findOrThrow(projectId, mcpId);
        entity.setStatus("DISABLED");
        mcpServerMapper.update(entity);
        auditService.recordAudit(projectId, "MCP", mcpId, "DISABLE", "SUCCESS",
                "MCP " + entity.getName() + " disabled", null, null);
        return toDto(entity);
    }

    public ProjectMcpServerDto testMcpConnection(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = findOrThrow(projectId, mcpId);

        try {
            String configJson = entity.getConfigJson();
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            String serverType = entity.getServerType();

            boolean healthy = false;
            String message = "";

            if ("HTTP".equals(serverType) || "SSE".equals(serverType)) {
                String url = (String) config.get("url");
                if (url != null) {
                    try {
                        URL testUrl = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) testUrl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        int code = conn.getResponseCode();
                        healthy = code < 500;
                        message = "HTTP " + code;
                    } catch (Exception e) {
                        message = "Connection failed: " + e.getMessage();
                    }
                } else {
                    message = "No URL configured";
                }
            } else if ("STDIO".equals(serverType)) {
                String command = (String) config.get("command");
                if (command != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        boolean completed = p.waitFor(5, TimeUnit.SECONDS);
                        if (completed) {
                            healthy = p.exitValue() == 0;
                            message = "Exit code: " + p.exitValue();
                        } else {
                            p.destroyForcibly();
                            healthy = true;
                            message = "Process started successfully";
                        }
                    } catch (Exception e) {
                        message = "Execution failed: " + e.getMessage();
                    }
                } else {
                    message = "No command configured";
                }
            } else {
                message = "Unknown server type: " + serverType;
            }

            entity.setLastHealthStatus(healthy ? "OK" : "FAILED");
            entity.setLastHealthMessage(message);
            entity.setLastCheckedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            mcpServerMapper.update(entity);

            auditService.recordAudit(projectId, "MCP", mcpId, "TEST", "SUCCESS",
                    "MCP " + entity.getName() + " health check: " + message, null, null);
        } catch (Exception e) {
            entity.setLastHealthStatus("FAILED");
            entity.setLastHealthMessage("Test error: " + e.getMessage());
            entity.setLastCheckedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            mcpServerMapper.update(entity);

            auditService.recordAudit(projectId, "MCP", mcpId, "TEST", "FAILED",
                    "MCP " + entity.getName() + " test failed: " + e.getMessage(), null, null);
        }
        return toDto(entity);
    }

    public List<ProjectMcpToolSnapshotDto> refreshMcpTools(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = findOrThrow(projectId, mcpId);
        toolSnapshotMapper.deleteByMcpServerId(mcpId);
        auditService.recordAudit(projectId, "MCP", mcpId, "REFRESH_TOOLS", "SUCCESS",
                "MCP " + entity.getName() + " tools refreshed", null, null);
        return List.of();
    }

    public void refreshAllMcps(String projectId) {
        importMcpConfig(projectId);
        List<ProjectMcpServerEntity> enabled = mcpServerMapper.findByProjectId(projectId)
                .stream().filter(e -> "ENABLED".equals(e.getStatus())).toList();
        for (ProjectMcpServerEntity entity : enabled) {
            testMcpConnection(projectId, entity.getId());
        }
        auditService.recordAudit(projectId, "MCP", "", "REFRESH_ALL", "SUCCESS",
                "Refreshed all MCP servers for project " + projectId, null, null);
    }

    private ProjectMcpServerEntity findOrThrow(String projectId, String mcpId) {
        ProjectMcpServerEntity entity = mcpServerMapper.findById(mcpId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found");
        }
        return entity;
    }

    private ProjectMcpServerDto toDto(ProjectMcpServerEntity entity) {
        Map<String, Object> configSummary = buildMaskedConfigSummary(entity);
        int toolCount = toolSnapshotMapper.countByMcpServerId(entity.getId());

        return new ProjectMcpServerDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getServerType(),
                entity.getStatus(),
                configSummary,
                entity.getConfigChecksum(),
                entity.getLastHealthStatus(),
                entity.getLastHealthMessage(),
                parseDateTime(entity.getLastCheckedAt()),
                entity.getCreatedBy(),
                parseDateTime(entity.getCreatedAt()),
                parseDateTime(entity.getUpdatedAt()),
                toolCount
        );
    }

    private Map<String, Object> buildMaskedConfigSummary(ProjectMcpServerEntity entity) {
        if (entity.getConfigJson() == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(entity.getConfigJson(), Map.class);
            Map<String, Object> summary = new LinkedHashMap<>();

            if (config.containsKey("url")) summary.put("url", config.get("url"));
            if (config.containsKey("command")) summary.put("command", config.get("command"));
            if (config.containsKey("args")) summary.put("args", config.get("args"));

            if (config.containsKey("headers")) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) config.get("headers");
                summary.put("headers", headers.keySet().stream()
                        .map(k -> k + ": ****")
                        .toList());
            }

            if (config.containsKey("env")) {
                @SuppressWarnings("unchecked")
                Map<String, String> env = (Map<String, String>) config.get("env");
                summary.put("envKeys", new ArrayList<>(env.keySet()));
            }

            return summary;
        } catch (Exception e) {
            return Map.of("error", "Failed to parse config");
        }
    }

    private String determineServerType(Map<String, Object> config) {
        if (config.containsKey("url")) {
            String url = (String) config.get("url");
            if (url != null && url.contains("/sse")) return "SSE";
            return "HTTP";
        }
        return "STDIO";
    }

    private String computeChecksum(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "error";
        }
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
