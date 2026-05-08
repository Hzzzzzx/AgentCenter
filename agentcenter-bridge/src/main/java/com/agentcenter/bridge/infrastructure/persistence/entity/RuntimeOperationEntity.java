package com.agentcenter.bridge.infrastructure.persistence.entity;

public class RuntimeOperationEntity {
    private String id;
    private String projectId;
    private String runtimeType;
    private String operationType;
    private String status;
    private String idempotencyKey;
    private String messageId;
    private String correlationId;
    private String agentSessionId;
    private String runtimeSessionId;
    private String workItemId;
    private String workflowInstanceId;
    private String workflowNodeInstanceId;
    private String resourceType;
    private String resourceId;
    private String commandJson;
    private String ackJson;
    private String lastEventType;
    private String lastEventId;
    private String externalStatus;
    private String externalOperationId;
    private String errorCode;
    private String errorMessage;
    private String deadlineAt;
    private String startedAt;
    private String completedAt;
    private String createdBy;
    private String createdAt;
    private String updatedAt;

    public RuntimeOperationEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getWorkflowNodeInstanceId() { return workflowNodeInstanceId; }
    public void setWorkflowNodeInstanceId(String workflowNodeInstanceId) { this.workflowNodeInstanceId = workflowNodeInstanceId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getCommandJson() { return commandJson; }
    public void setCommandJson(String commandJson) { this.commandJson = commandJson; }
    public String getAckJson() { return ackJson; }
    public void setAckJson(String ackJson) { this.ackJson = ackJson; }
    public String getLastEventType() { return lastEventType; }
    public void setLastEventType(String lastEventType) { this.lastEventType = lastEventType; }
    public String getLastEventId() { return lastEventId; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }
    public String getExternalStatus() { return externalStatus; }
    public void setExternalStatus(String externalStatus) { this.externalStatus = externalStatus; }
    public String getExternalOperationId() { return externalOperationId; }
    public void setExternalOperationId(String externalOperationId) { this.externalOperationId = externalOperationId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(String deadlineAt) { this.deadlineAt = deadlineAt; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
