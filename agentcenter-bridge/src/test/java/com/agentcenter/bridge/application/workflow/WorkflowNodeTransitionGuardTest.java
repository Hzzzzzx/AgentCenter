package com.agentcenter.bridge.application.workflow;

import java.util.List;

import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeState;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateStatus;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowNodeTransitionGuardTest {

    private final WorkflowNodeTransitionGuard guard = new WorkflowNodeTransitionGuard();

    @Test
    void rejectsReadyWhenPendingInteractionsExist() {
        WorkflowTransitionDecision decision = guard.decide(
                instance("node-1"),
                node("node-1"),
                readyState(),
                successResult("# PRD\n\n<!-- AGENTCENTER_NODE_STATE\nstatus: READY_TO_ADVANCE\nreason: done\n-->"),
                resumeState(List.of(new WorkflowResumeState.PendingInteraction(
                        "conf-1", "DECISION", "选择方案"))));

        assertThat(decision.type()).isEqualTo(WorkflowTransitionDecision.Type.REJECT_KEEP_WAITING);
        assertThat(decision.effectiveState().getStatus()).isEqualTo(WorkflowNodeStateStatus.NEEDS_USER_INPUT);
        assertThat(decision.acceptsReady()).isFalse();
    }

    @Test
    void rejectsReadyForStaleCurrentNode() {
        WorkflowTransitionDecision decision = guard.decide(
                instance("node-2"),
                node("node-1"),
                readyState(),
                successResult("# PRD\n\n<!-- AGENTCENTER_NODE_STATE\nstatus: READY_TO_ADVANCE\nreason: done\n-->"),
                resumeState(List.of()));

        assertThat(decision.type()).isEqualTo(WorkflowTransitionDecision.Type.REJECT_BLOCKED);
        assertThat(decision.effectiveState().getStatus()).isEqualTo(WorkflowNodeStateStatus.BLOCKED);
    }

    @Test
    void acceptsReadyWhenBridgeStateIsClearAndOutputExists() {
        WorkflowTransitionDecision decision = guard.decide(
                instance("node-1"),
                node("node-1"),
                readyState(),
                successResult("# PRD\n\n<!-- AGENTCENTER_NODE_STATE\nstatus: READY_TO_ADVANCE\nreason: done\n-->"),
                resumeState(List.of()));

        assertThat(decision.type()).isEqualTo(WorkflowTransitionDecision.Type.ACCEPT_READY);
        assertThat(decision.acceptsReady()).isTrue();
    }

    @Test
    void acceptsNonReadyStatesWithoutReadyGuard() {
        WorkflowNodeState inProgress = new WorkflowNodeState(
                WorkflowNodeStateStatus.IN_PROGRESS,
                "still working",
                null,
                List.of(),
                null);

        WorkflowTransitionDecision decision = guard.decide(
                instance("node-1"),
                node("node-1"),
                inProgress,
                successResult("partial output"),
                resumeState(List.of(new WorkflowResumeState.PendingInteraction(
                        "conf-1", "INPUT", "补充信息"))));

        assertThat(decision.type()).isEqualTo(WorkflowTransitionDecision.Type.ACCEPT_IN_PROGRESS);
        assertThat(decision.effectiveState()).isEqualTo(inProgress);
    }

    private WorkflowInstanceEntity instance(String currentNodeId) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId("wf-1");
        instance.setCurrentNodeInstanceId(currentNodeId);
        return instance;
    }

    private WorkflowNodeInstanceEntity node(String nodeId) {
        WorkflowNodeInstanceEntity node = new WorkflowNodeInstanceEntity();
        node.setId(nodeId);
        return node;
    }

    private WorkflowNodeState readyState() {
        return new WorkflowNodeState(
                WorkflowNodeStateStatus.READY_TO_ADVANCE,
                "done",
                "artifact.md",
                List.of(),
                null);
    }

    private SkillRunResult successResult(String output) {
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    private WorkflowResumeState resumeState(List<WorkflowResumeState.PendingInteraction> pendingInteractions) {
        return new WorkflowResumeState(
                "wf-1",
                "node-1",
                "wi-1",
                "project-1",
                "runtime-1",
                pendingInteractions.isEmpty() ? "NODE_EXECUTION" : "PENDING_USER_INTERACTION",
                "RUNNING",
                "prd-design",
                "invoke-1",
                List.of(),
                pendingInteractions);
    }
}
