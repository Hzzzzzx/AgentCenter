package com.agentcenter.bridge.infrastructure.persistence.entity;

public class WorkflowNodeInstanceEntity {
    private String id;
    private String workflowInstanceId;
    private String nodeDefinitionId;
    private String status;
    private String inputArtifactId;
    private String outputArtifactId;
    private String agentSessionId;
    private String runtimeSessionId;
    private String startedAt;
    private String completedAt;
    private String errorMessage;
    private Integer version;
    private String nodeKind;
    private String origin;
    private String parentNodeInstanceId;
    private String stageKey;
    private String skillName;
    private String summary;
    private String reason;
    private Integer sequenceNo;

    public WorkflowNodeInstanceEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getNodeDefinitionId() { return nodeDefinitionId; }
    public void setNodeDefinitionId(String nodeDefinitionId) { this.nodeDefinitionId = nodeDefinitionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputArtifactId() { return inputArtifactId; }
    public void setInputArtifactId(String inputArtifactId) { this.inputArtifactId = inputArtifactId; }
    public String getOutputArtifactId() { return outputArtifactId; }
    public void setOutputArtifactId(String outputArtifactId) { this.outputArtifactId = outputArtifactId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getNodeKind() { return nodeKind; }
    public void setNodeKind(String nodeKind) { this.nodeKind = nodeKind; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getParentNodeInstanceId() { return parentNodeInstanceId; }
    public void setParentNodeInstanceId(String parentNodeInstanceId) { this.parentNodeInstanceId = parentNodeInstanceId; }
    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
}
