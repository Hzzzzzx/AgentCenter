package com.agentcenter.bridge.infrastructure.persistence.entity;

public class WorkflowInstanceEntity {
    private String id;
    private String workItemId;
    private String workflowDefinitionId;
    private String status;
    private String currentNodeInstanceId;
    private String startedAt;
    private String completedAt;
    private String createdAt;
    private String updatedAt;

    public WorkflowInstanceEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(String workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentNodeInstanceId() { return currentNodeInstanceId; }
    public void setCurrentNodeInstanceId(String currentNodeInstanceId) { this.currentNodeInstanceId = currentNodeInstanceId; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
