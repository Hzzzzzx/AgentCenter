package com.agentcenter.bridge.application.workflow;

import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeState;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateParser;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateStatus;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;

/**
 * Accepts or rejects agent-reported workflow transitions using Bridge-owned state.
 */
public class WorkflowNodeTransitionGuard {

    public WorkflowTransitionDecision decide(WorkflowInstanceEntity instance,
                                             WorkflowNodeInstanceEntity node,
                                             WorkflowNodeState reportedState,
                                             SkillRunResult result,
                                             WorkflowResumeState resumeState) {
        if (reportedState.getStatus() != WorkflowNodeStateStatus.READY_TO_ADVANCE) {
            return WorkflowTransitionDecision.accept(reportedState);
        }

        String currentNodeId = instance != null ? instance.getCurrentNodeInstanceId() : null;
        if (currentNodeId != null && !currentNodeId.isBlank() && !currentNodeId.equals(node.getId())) {
            return WorkflowTransitionDecision.rejectBlocked(
                    "Agent reported READY_TO_ADVANCE for a node that is no longer the workflow current node.");
        }

        if (resumeState != null && resumeState.hasPendingInteractions()) {
            return WorkflowTransitionDecision.rejectKeepWaiting(
                    "当前节点仍有未处理交互，拒绝 READY_TO_ADVANCE；请先等待用户完成确认或补充输入。");
        }

        if (result.outputContent() == null || WorkflowNodeStateParser.stripStateBlock(result.outputContent()).isBlank()) {
            return WorkflowTransitionDecision.rejectBlocked(
                    "Agent reported READY_TO_ADVANCE without a persistable final artifact.");
        }

        return WorkflowTransitionDecision.accept(reportedState);
    }
}
