package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectMcpServerEntity {
    private String id;
    private String projectId;
    private String name;
    private String serverType;
    private String status;
    private String configJson;
    private String configChecksum;
    private String lastHealthStatus;
    private String lastHealthMessage;
    private String lastCheckedAt;
    private String createdBy;
    private String createdAt;
    private String updatedAt;

    public ProjectMcpServerEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getConfigChecksum() { return configChecksum; }
    public void setConfigChecksum(String configChecksum) { this.configChecksum = configChecksum; }
    public String getLastHealthStatus() { return lastHealthStatus; }
    public void setLastHealthStatus(String lastHealthStatus) { this.lastHealthStatus = lastHealthStatus; }
    public String getLastHealthMessage() { return lastHealthMessage; }
    public void setLastHealthMessage(String lastHealthMessage) { this.lastHealthMessage = lastHealthMessage; }
    public String getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(String lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
