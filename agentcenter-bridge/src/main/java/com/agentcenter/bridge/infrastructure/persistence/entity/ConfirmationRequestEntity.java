package com.agentcenter.bridge.infrastructure.persistence.entity;

public class ConfirmationRequestEntity {
    private String id;
    private String requestType;
    private String status;
    private String projectId;
    private String spaceId;
    private String iterationId;
    private String workItemId;
    private String workflowInstanceId;
    private String workflowNodeInstanceId;
    private String agentSessionId;
    private String runtimeType;
    private String runtimeSessionId;
    private String runtimeEventId;
    private String skillName;
    private String mcpServer;
    private String mcpTool;
    private String title;
    private String content;
    private String contextSummary;
    private String optionsJson;
    private String priority;
    private String requiredRole;
    private String assigneeUserId;
    private String createdAt;
    private String updatedAt;
    private String resolvedBy;
    private String resolvedAt;
    private String resolutionComment;
    private String resolutionPayloadJson;

    public ConfirmationRequestEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getIterationId() { return iterationId; }
    public void setIterationId(String iterationId) { this.iterationId = iterationId; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getWorkflowNodeInstanceId() { return workflowNodeInstanceId; }
    public void setWorkflowNodeInstanceId(String workflowNodeInstanceId) { this.workflowNodeInstanceId = workflowNodeInstanceId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getRuntimeEventId() { return runtimeEventId; }
    public void setRuntimeEventId(String runtimeEventId) { this.runtimeEventId = runtimeEventId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getMcpServer() { return mcpServer; }
    public void setMcpServer(String mcpServer) { this.mcpServer = mcpServer; }
    public String getMcpTool() { return mcpTool; }
    public void setMcpTool(String mcpTool) { this.mcpTool = mcpTool; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContextSummary() { return contextSummary; }
    public void setContextSummary(String contextSummary) { this.contextSummary = contextSummary; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getRequiredRole() { return requiredRole; }
    public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionComment() { return resolutionComment; }
    public void setResolutionComment(String resolutionComment) { this.resolutionComment = resolutionComment; }
    public String getResolutionPayloadJson() { return resolutionPayloadJson; }
    public void setResolutionPayloadJson(String resolutionPayloadJson) { this.resolutionPayloadJson = resolutionPayloadJson; }
}
