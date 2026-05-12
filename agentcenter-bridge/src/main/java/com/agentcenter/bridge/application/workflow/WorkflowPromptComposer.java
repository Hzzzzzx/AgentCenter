package com.agentcenter.bridge.application.workflow;

import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;

/**
 * Assembles workflow node execution prompts.
 * Combines work item context, node context, upstream artifacts,
 * interaction history, and the node state protocol instruction.
 */
public class WorkflowPromptComposer {

    private final WorkflowNodeInstructionComposer instructionComposer;

    public WorkflowPromptComposer() {
        this.instructionComposer = new WorkflowNodeInstructionComposer();
    }

    WorkflowPromptComposer(WorkflowNodeInstructionComposer instructionComposer) {
        this.instructionComposer = instructionComposer;
    }

    public SkillInvocationRequest composeInvocationRequest(String skillName, String inputContext) {
        String instruction = instructionComposer.composeProtocolInstruction();
        return SkillInvocationRequest.userPromptInjection(skillName, inputContext, instruction);
    }

    public String composeNodePrompt(NodeExecutionContext context) {
        StringBuilder sb = new StringBuilder();

        appendWorkItemSection(sb, context);
        appendCurrentNodeSection(sb, context);
        appendUpstreamArtifactSection(sb, context);
        appendInteractionHistorySection(sb, context);
        appendSupplementalInputSection(sb, context);
        sb.append(instructionComposer.composeProtocolInstruction());

        return sb.toString();
    }

    private void appendWorkItemSection(StringBuilder sb, NodeExecutionContext context) {
        NodeExecutionContext.WorkItemData wi = context.getWorkItem();
        sb.append("# 工作流节点执行输入\n\n");
        sb.append("## 工作项\n");
        appendField(sb, "ID", wi.getId());
        appendField(sb, "编号", wi.getCode());
        appendField(sb, "标题", wi.getTitle());
        appendField(sb, "类型", wi.getType());
        appendField(sb, "状态", wi.getStatus());
        appendField(sb, "优先级", wi.getPriority());
        appendField(sb, "项目", wi.getProjectId());
        appendField(sb, "空间", wi.getSpaceId());
        appendField(sb, "迭代", wi.getIterationId());
        appendField(sb, "负责人", wi.getAssigneeUserId());
        sb.append("- 描述：\n\n");
        sb.append("```text\n");
        sb.append(nonBlank(wi.getDescription(), "暂无描述"));
        sb.append("\n```\n\n");
    }

    private void appendCurrentNodeSection(StringBuilder sb, NodeExecutionContext context) {
        NodeExecutionContext.NodeData node = context.getNode();
        sb.append("## 当前节点\n");
        appendField(sb, "节点ID", node.getNodeId());
        appendField(sb, "节点名称", node.getNodeName());
        appendField(sb, "节点Key", node.getNodeKey());
        appendField(sb, "阶段Key", node.getStageKey());
        appendField(sb, "Skill", node.getSkillName());
        appendField(sb, "输入策略", node.getInputPolicy());
        appendField(sb, "输出类型", node.getOutputType());
        appendField(sb, "需要确认", node.isRequiredConfirmation() ? "是" : "否");
        if (node.getStageGoal() != null && !node.getStageGoal().isBlank()) {
            appendField(sb, "阶段目标", node.getStageGoal());
        }
        sb.append("\n");
    }

    private void appendUpstreamArtifactSection(StringBuilder sb, NodeExecutionContext context) {
        NodeExecutionContext.UpstreamArtifactData artifact = context.getUpstreamArtifact();
        if (artifact != null && artifact.getContent() != null) {
            sb.append("## 上游产物\n");
            appendField(sb, "artifactId", artifact.getArtifactId());
            appendField(sb, "title", artifact.getTitle());
            appendField(sb, "type", artifact.getArtifactType());
            appendField(sb, "sourceNodeInstanceId", artifact.getSourceNodeInstanceId());
            sb.append("\n### 上游产物内容\n\n");
            sb.append("```markdown\n");
            sb.append(artifact.getContent());
            sb.append("\n```\n\n");
        } else {
            sb.append("## 上游产物\n");
            sb.append("无。该节点应基于工作项本身生成结果。\n\n");
        }
    }

    private void appendInteractionHistorySection(StringBuilder sb, NodeExecutionContext context) {
        if (context.getResolvedInteractions().isEmpty()) {
            return;
        }

        sb.append("## 用户交互回答历史\n");
        for (NodeExecutionContext.ResolvedInteraction interaction : context.getResolvedInteractions()) {
            appendField(sb, "确认项", nonBlank(interaction.getTitle(), interaction.getId()));
            appendField(sb, "原始问题", interaction.getQuestion());
            appendField(sb, "交互类型", interaction.getType());
            appendField(sb, "用户处理", interaction.getResolutionPayload());
            appendField(sb, "用户备注", interaction.getComment());
            sb.append("\n");
        }
    }

    private void appendSupplementalInputSection(StringBuilder sb, NodeExecutionContext context) {
        if (context.getSupplementalInput() == null || context.getSupplementalInput().isBlank()) {
            return;
        }

        sb.append("## 用户本轮输入（优先执行）\n\n");
        sb.append("以下内容来自用户在页面中的主动介入。它是当前节点的直接指令；请优先按它继续、修正或扩展当前输出，不要只回复等待平台推进。\n\n");
        sb.append("```text\n");
        sb.append(context.getSupplementalInput());
        sb.append("\n```\n\n");
    }

    private void appendField(StringBuilder sb, String label, String value) {
        sb.append("- ").append(label).append("：").append(nonBlank(value, "未提供")).append("\n");
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
