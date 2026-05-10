package com.agentcenter.bridge.domain.workflow.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parsed state from an AGENTCENTER_NODE_STATE block in Agent output.
 * Immutable value object representing the Agent's signal about the current workflow node.
 */
public class WorkflowNodeState {

    private final WorkflowNodeStateStatus status;
    private final String reason;
    private final String artifactTitle;
    private final List<WorkflowNodeInteraction> interactions;
    private final String rawBlock;

    public WorkflowNodeState(WorkflowNodeStateStatus status, String reason,
                             String artifactTitle, List<WorkflowNodeInteraction> interactions,
                             String rawBlock) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.artifactTitle = artifactTitle;
        this.interactions = interactions != null
                ? Collections.unmodifiableList(new ArrayList<>(interactions))
                : Collections.emptyList();
        this.rawBlock = rawBlock;
    }

    public WorkflowNodeStateStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getArtifactTitle() {
        return artifactTitle;
    }

    public List<WorkflowNodeInteraction> getInteractions() {
        return interactions;
    }

    public String getRawBlock() {
        return rawBlock;
    }

    public static WorkflowNodeState defaultInProgress() {
        return new WorkflowNodeState(
                WorkflowNodeStateStatus.IN_PROGRESS,
                "No state block found",
                null,
                List.of(),
                null
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowNodeState that)) return false;
        return status == that.status
                && Objects.equals(reason, that.reason)
                && Objects.equals(artifactTitle, that.artifactTitle)
                && Objects.equals(interactions, that.interactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, reason, artifactTitle, interactions);
    }

    @Override
    public String toString() {
        return "WorkflowNodeState{status=" + status
                + ", reason='" + reason + "'"
                + ", artifactTitle='" + artifactTitle + "'"
                + ", interactions=" + interactions.size()
                + '}';
    }
}
