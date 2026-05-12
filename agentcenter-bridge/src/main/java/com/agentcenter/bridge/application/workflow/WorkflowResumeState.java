package com.agentcenter.bridge.application.workflow;

import java.util.List;

/**
 * Bridge-owned workflow state injected before every runtime skill invocation.
 * This is the source of truth when the runtime has compacted its conversation.
 */
public record WorkflowResumeState(
        String workflowInstanceId,
        String workflowNodeInstanceId,
        String workItemId,
        String projectId,
        String runtimeSessionId,
        String currentGate,
        String nodeStatus,
        String skillName,
        String invocationId,
        List<WorkflowStep> workflowSteps,
        List<PendingInteraction> pendingInteractions
) {
    public WorkflowResumeState {
        workflowSteps = workflowSteps == null ? List.of() : List.copyOf(workflowSteps);
        pendingInteractions = pendingInteractions == null ? List.of() : List.copyOf(pendingInteractions);
    }

    public boolean hasPendingInteractions() {
        return !pendingInteractions.isEmpty();
    }

    public record WorkflowStep(
            String nodeInstanceId,
            String nodeDefinitionId,
            String nodeKey,
            String nodeName,
            int orderNo,
            String skillName,
            String stageKey,
            String status,
            boolean current
    ) {
    }

    public record PendingInteraction(
            String id,
            String type,
            String title
    ) {
    }
}
