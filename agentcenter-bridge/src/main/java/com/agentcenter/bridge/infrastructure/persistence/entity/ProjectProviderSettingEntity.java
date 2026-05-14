package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectProviderSettingEntity {
    private String id;
    private String activeProviderId;
    private String activeProjectContextId;
    private String activeProjectSpaceId;
    private String activeProjectIterationId;
    private String activeProjectName;
    private String activeExternalProjectId;
    private String activeExternalSpaceId;
    private String activeExternalIterationId;
    private String updatedAt;

    public ProjectProviderSettingEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActiveProviderId() { return activeProviderId; }
    public void setActiveProviderId(String activeProviderId) { this.activeProviderId = activeProviderId; }
    public String getActiveProjectContextId() { return activeProjectContextId; }
    public void setActiveProjectContextId(String activeProjectContextId) { this.activeProjectContextId = activeProjectContextId; }
    public String getActiveProjectSpaceId() { return activeProjectSpaceId; }
    public void setActiveProjectSpaceId(String activeProjectSpaceId) { this.activeProjectSpaceId = activeProjectSpaceId; }
    public String getActiveProjectIterationId() { return activeProjectIterationId; }
    public void setActiveProjectIterationId(String activeProjectIterationId) { this.activeProjectIterationId = activeProjectIterationId; }
    public String getActiveProjectName() { return activeProjectName; }
    public void setActiveProjectName(String activeProjectName) { this.activeProjectName = activeProjectName; }
    public String getActiveExternalProjectId() { return activeExternalProjectId; }
    public void setActiveExternalProjectId(String activeExternalProjectId) { this.activeExternalProjectId = activeExternalProjectId; }
    public String getActiveExternalSpaceId() { return activeExternalSpaceId; }
    public void setActiveExternalSpaceId(String activeExternalSpaceId) { this.activeExternalSpaceId = activeExternalSpaceId; }
    public String getActiveExternalIterationId() { return activeExternalIterationId; }
    public void setActiveExternalIterationId(String activeExternalIterationId) { this.activeExternalIterationId = activeExternalIterationId; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
