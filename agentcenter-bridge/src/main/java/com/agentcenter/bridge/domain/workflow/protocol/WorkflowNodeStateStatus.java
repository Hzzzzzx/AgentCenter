package com.agentcenter.bridge.domain.workflow.protocol;

/**
 * Status of a workflow node as reported by the Agent via AGENTCENTER_NODE_STATE protocol.
 */
public enum WorkflowNodeStateStatus {
    /** Default: agent is producing incremental output, no node advance. */
    IN_PROGRESS,
    /** Agent needs user input; create interactions and stay on current node. */
    NEEDS_USER_INPUT,
    /** Current node completed; save artifact and advance to next node. */
    READY_TO_ADVANCE,
    /** Agent is blocked by error, permission, or missing information. */
    BLOCKED
}
