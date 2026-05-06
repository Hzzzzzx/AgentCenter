package com.agentcenter.bridge.infrastructure.persistence.entity;

public class WorkflowNodeDefinitionEntity {
    private String id;
    private String workflowDefinitionId;
    private String nodeKey;
    private String name;
    private Integer orderNo;
    private String skillName;
    private String inputPolicy;
    private String outputArtifactType;
    private String outputNameTemplate;
    private Integer retryLimit;
    private Integer timeoutSeconds;
    private Boolean requiredConfirmation;
    private String stageKey;
    private String stageGoal;
    private String recommendedSkillNamesJson;
    private Boolean allowDynamicActions;
    private String confirmationPolicy;

    public WorkflowNodeDefinitionEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(String workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getInputPolicy() { return inputPolicy; }
    public void setInputPolicy(String inputPolicy) { this.inputPolicy = inputPolicy; }
    public String getOutputArtifactType() { return outputArtifactType; }
    public void setOutputArtifactType(String outputArtifactType) { this.outputArtifactType = outputArtifactType; }
    public String getOutputNameTemplate() { return outputNameTemplate; }
    public void setOutputNameTemplate(String outputNameTemplate) { this.outputNameTemplate = outputNameTemplate; }
    public Integer getRetryLimit() { return retryLimit; }
    public void setRetryLimit(Integer retryLimit) { this.retryLimit = retryLimit; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Boolean getRequiredConfirmation() { return requiredConfirmation; }
    public void setRequiredConfirmation(Boolean requiredConfirmation) { this.requiredConfirmation = requiredConfirmation; }
    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getStageGoal() { return stageGoal; }
    public void setStageGoal(String stageGoal) { this.stageGoal = stageGoal; }
    public String getRecommendedSkillNamesJson() { return recommendedSkillNamesJson; }
    public void setRecommendedSkillNamesJson(String recommendedSkillNamesJson) { this.recommendedSkillNamesJson = recommendedSkillNamesJson; }
    public Boolean getAllowDynamicActions() { return allowDynamicActions; }
    public void setAllowDynamicActions(Boolean allowDynamicActions) { this.allowDynamicActions = allowDynamicActions; }
    public String getConfirmationPolicy() { return confirmationPolicy; }
    public void setConfirmationPolicy(String confirmationPolicy) { this.confirmationPolicy = confirmationPolicy; }
}
