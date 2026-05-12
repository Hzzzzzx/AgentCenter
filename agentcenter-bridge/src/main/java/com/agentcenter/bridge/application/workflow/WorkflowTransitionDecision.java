package com.agentcenter.bridge.application.workflow;

import java.util.List;

import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeState;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateStatus;

/**
 * Result of applying Bridge-owned transition rules to an agent-reported node state.
 */
public record WorkflowTransitionDecision(
        Type type,
        WorkflowNodeState effectiveState,
        String reason
) {
    public enum Type {
        ACCEPT_READY,
        ACCEPT_NEEDS_USER_INPUT,
        ACCEPT_IN_PROGRESS,
        ACCEPT_BLOCKED,
        REJECT_KEEP_WAITING,
        REJECT_BLOCKED
    }

    public static WorkflowTransitionDecision accept(WorkflowNodeState state) {
        return switch (state.getStatus()) {
            case READY_TO_ADVANCE -> new WorkflowTransitionDecision(Type.ACCEPT_READY, state, state.getReason());
            case NEEDS_USER_INPUT -> new WorkflowTransitionDecision(Type.ACCEPT_NEEDS_USER_INPUT, state, state.getReason());
            case IN_PROGRESS -> new WorkflowTransitionDecision(Type.ACCEPT_IN_PROGRESS, state, state.getReason());
            case BLOCKED -> new WorkflowTransitionDecision(Type.ACCEPT_BLOCKED, state, state.getReason());
        };
    }

    public static WorkflowTransitionDecision rejectKeepWaiting(String reason) {
        WorkflowNodeState effective = new WorkflowNodeState(
                WorkflowNodeStateStatus.NEEDS_USER_INPUT,
                reason,
                null,
                List.of(),
                null);
        return new WorkflowTransitionDecision(Type.REJECT_KEEP_WAITING, effective, reason);
    }

    public static WorkflowTransitionDecision rejectBlocked(String reason) {
        WorkflowNodeState effective = new WorkflowNodeState(
                WorkflowNodeStateStatus.BLOCKED,
                reason,
                null,
                List.of(),
                null);
        return new WorkflowTransitionDecision(Type.REJECT_BLOCKED, effective, reason);
    }

    public boolean acceptsReady() {
        return type == Type.ACCEPT_READY;
    }

    public boolean reusesPendingInteraction() {
        return type == Type.REJECT_KEEP_WAITING;
    }

    public boolean rejected() {
        return type == Type.REJECT_KEEP_WAITING || type == Type.REJECT_BLOCKED;
    }
}
