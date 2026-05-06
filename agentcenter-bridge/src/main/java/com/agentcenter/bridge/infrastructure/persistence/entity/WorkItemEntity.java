package com.agentcenter.bridge.infrastructure.persistence.entity;

public class WorkItemEntity {
    private String id;
    private String code;
    private String type;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String projectId;
    private String spaceId;
    private String iterationId;
    private String ownerUserId;
    private String assigneeUserId;
    private String currentWorkflowInstanceId;
    private Integer version;
    private String createdAt;
    private String updatedAt;

    public WorkItemEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getIterationId() { return iterationId; }
    public void setIterationId(String iterationId) { this.iterationId = iterationId; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getCurrentWorkflowInstanceId() { return currentWorkflowInstanceId; }
    public void setCurrentWorkflowInstanceId(String currentWorkflowInstanceId) { this.currentWorkflowInstanceId = currentWorkflowInstanceId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
