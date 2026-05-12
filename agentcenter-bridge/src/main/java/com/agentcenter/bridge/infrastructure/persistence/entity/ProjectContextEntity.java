package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectContextEntity {
    private String id;
    private String providerId;
    private String externalProjectId;
    private String projectName;
    private String externalCloudeReqProjectId;
    private String cloudeReqProjectName;
    private Integer active;
    private String extraJson;
    private String createdAt;
    private String updatedAt;

    public ProjectContextEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getExternalProjectId() { return externalProjectId; }
    public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getExternalCloudeReqProjectId() { return externalCloudeReqProjectId; }
    public void setExternalCloudeReqProjectId(String externalCloudeReqProjectId) { this.externalCloudeReqProjectId = externalCloudeReqProjectId; }
    public String getCloudeReqProjectName() { return cloudeReqProjectName; }
    public void setCloudeReqProjectName(String cloudeReqProjectName) { this.cloudeReqProjectName = cloudeReqProjectName; }
    public Integer getActive() { return active; }
    public void setActive(Integer active) { this.active = active; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
