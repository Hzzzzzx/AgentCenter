package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectIterationEntity {
    private String id;
    private String providerId;
    private String projectContextId;
    private String projectSpaceId;
    private String externalIterationId;
    private String iterationName;
    private String status;
    private String startAt;
    private String endAt;
    private String extraJson;
    private String createdAt;
    private String updatedAt;

    public ProjectIterationEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProjectContextId() { return projectContextId; }
    public void setProjectContextId(String projectContextId) { this.projectContextId = projectContextId; }
    public String getProjectSpaceId() { return projectSpaceId; }
    public void setProjectSpaceId(String projectSpaceId) { this.projectSpaceId = projectSpaceId; }
    public String getExternalIterationId() { return externalIterationId; }
    public void setExternalIterationId(String externalIterationId) { this.externalIterationId = externalIterationId; }
    public String getIterationName() { return iterationName; }
    public void setIterationName(String iterationName) { this.iterationName = iterationName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
