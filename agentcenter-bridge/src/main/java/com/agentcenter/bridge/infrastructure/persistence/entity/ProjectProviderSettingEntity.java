package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectProviderSettingEntity {
    private String id;
    private String activeProviderId;
    private String activeProjectContextId;
    private String activeProjectSpaceId;
    private String activeProjectIterationId;
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
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
