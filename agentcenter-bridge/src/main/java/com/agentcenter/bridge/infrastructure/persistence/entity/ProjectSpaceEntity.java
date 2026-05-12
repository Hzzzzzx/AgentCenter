package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectSpaceEntity {
    private String id;
    private String providerId;
    private String projectContextId;
    private String externalSpaceId;
    private String spaceName;
    private String extraJson;
    private String createdAt;
    private String updatedAt;

    public ProjectSpaceEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProjectContextId() { return projectContextId; }
    public void setProjectContextId(String projectContextId) { this.projectContextId = projectContextId; }
    public String getExternalSpaceId() { return externalSpaceId; }
    public void setExternalSpaceId(String externalSpaceId) { this.externalSpaceId = externalSpaceId; }
    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
