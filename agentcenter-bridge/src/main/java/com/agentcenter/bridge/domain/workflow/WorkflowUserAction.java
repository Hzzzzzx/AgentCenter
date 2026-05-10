package com.agentcenter.bridge.domain.workflow;

public enum WorkflowUserAction {
    CONTINUE_CURRENT,
    ADVANCE_NEXT,
    RERUN_NODE,
    SKIP_NODE,
    PAUSE_WORKFLOW
}
