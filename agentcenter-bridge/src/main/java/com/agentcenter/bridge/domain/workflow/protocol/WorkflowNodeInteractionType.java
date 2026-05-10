package com.agentcenter.bridge.domain.workflow.protocol;

/**
 * Type of user interaction requested by the Agent in a workflow node.
 */
public enum WorkflowNodeInteractionType {
    ASK_USER,
    INPUT,
    DECISION,
    APPROVAL,
    ARTIFACT_REVIEW,
    PERMISSION,
    CUSTOM_FORM,
    RANKING,
    SCALE,
    BLOCKER
}
