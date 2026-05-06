package com.agentcenter.bridge.infrastructure.persistence.entity;

public class RuntimeEventEntity {
    private String id;
    private String sessionId;
    private String workItemId;
    private String workflowInstanceId;
    private String workflowNodeInstanceId;
    private String eventType;
    private String eventSource;
    private String payloadJson;
    private String createdAt;

    public RuntimeEventEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getWorkflowNodeInstanceId() { return workflowNodeInstanceId; }
    public void setWorkflowNodeInstanceId(String workflowNodeInstanceId) { this.workflowNodeInstanceId = workflowNodeInstanceId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventSource() { return eventSource; }
    public void setEventSource(String eventSource) { this.eventSource = eventSource; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
