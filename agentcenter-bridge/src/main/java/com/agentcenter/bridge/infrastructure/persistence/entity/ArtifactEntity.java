package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ArtifactEntity {
    private String id;
    private String workItemId;
    private String workflowInstanceId;
    private String workflowNodeInstanceId;
    private String sessionId;
    private String artifactType;
    private String title;
    private String content;
    private String storageUri;
    private Integer versionNo;
    private String createdBy;
    private String createdAt;

    public ArtifactEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getWorkflowNodeInstanceId() { return workflowNodeInstanceId; }
    public void setWorkflowNodeInstanceId(String workflowNodeInstanceId) { this.workflowNodeInstanceId = workflowNodeInstanceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
