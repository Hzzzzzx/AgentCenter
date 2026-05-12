package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ProjectDataSyncHistoryEntity {
    private String id;
    private String providerId;
    private String status;
    private Integer contextCount;
    private Integer workItemCount;
    private String activeProjectContextId;
    private String activeProjectSpaceId;
    private String activeProjectIterationId;
    private String resultJson;
    private String errorMessage;
    private String startedAt;
    private String completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getContextCount() { return contextCount; }
    public void setContextCount(Integer contextCount) { this.contextCount = contextCount; }
    public Integer getWorkItemCount() { return workItemCount; }
    public void setWorkItemCount(Integer workItemCount) { this.workItemCount = workItemCount; }
    public String getActiveProjectContextId() { return activeProjectContextId; }
    public void setActiveProjectContextId(String activeProjectContextId) { this.activeProjectContextId = activeProjectContextId; }
    public String getActiveProjectSpaceId() { return activeProjectSpaceId; }
    public void setActiveProjectSpaceId(String activeProjectSpaceId) { this.activeProjectSpaceId = activeProjectSpaceId; }
    public String getActiveProjectIterationId() { return activeProjectIterationId; }
    public void setActiveProjectIterationId(String activeProjectIterationId) { this.activeProjectIterationId = activeProjectIterationId; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
}
