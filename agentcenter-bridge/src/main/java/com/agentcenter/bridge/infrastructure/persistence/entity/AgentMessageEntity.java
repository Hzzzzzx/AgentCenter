package com.agentcenter.bridge.infrastructure.persistence.entity;

public class AgentMessageEntity {
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private String contentFormat;
    private String status;
    private Integer seqNo;
    private String runtimeMessageId;
    private String createdBy;
    private String createdAt;

    public AgentMessageEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentFormat() { return contentFormat; }
    public void setContentFormat(String contentFormat) { this.contentFormat = contentFormat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getRuntimeMessageId() { return runtimeMessageId; }
    public void setRuntimeMessageId(String runtimeMessageId) { this.runtimeMessageId = runtimeMessageId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
