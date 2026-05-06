package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectMcpToolSnapshotEntity {
    private String id;
    private String projectId;
    private String mcpServerId;
    private String toolName;
    private String description;
    private String inputSchemaJson;
    private Integer snapshotVersion;
    private String status;
    private String scannedAt;

    public ProjectMcpToolSnapshotEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(String mcpServerId) { this.mcpServerId = mcpServerId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }
    public Integer getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(Integer snapshotVersion) { this.snapshotVersion = snapshotVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScannedAt() { return scannedAt; }
    public void setScannedAt(String scannedAt) { this.scannedAt = scannedAt; }
}
