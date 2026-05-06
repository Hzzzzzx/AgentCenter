package com.agentcenter.bridge.infrastructure.persistence.entity;

public class WorkflowDefinitionEntity {
    private String id;
    private String workItemType;
    private String name;
    private Integer versionNo;
    private String status;
    private Boolean isDefault;
    private String createdAt;
    private String updatedAt;

    public WorkflowDefinitionEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkItemType() { return workItemType; }
    public void setWorkItemType(String workItemType) { this.workItemType = workItemType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
