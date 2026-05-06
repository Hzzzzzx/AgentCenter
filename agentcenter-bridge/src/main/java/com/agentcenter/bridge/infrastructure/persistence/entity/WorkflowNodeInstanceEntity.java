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
}
