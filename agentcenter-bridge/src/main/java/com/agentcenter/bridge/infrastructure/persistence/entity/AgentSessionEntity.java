package com.agentcenter.bridge.infrastructure.persistence.entity;

public class AgentSessionEntity {
    private String id;
    private String sessionType;
    private String title;
    private String workItemId;
    private String workflowInstanceId;
    private String runtimeType;
    private String runtimeSessionId;
    private String workingDirectory;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;

    public AgentSessionEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
