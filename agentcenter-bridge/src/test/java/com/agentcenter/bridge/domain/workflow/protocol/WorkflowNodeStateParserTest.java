package com.agentcenter.bridge.domain.workflow.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowNodeStateParser")
class WorkflowNodeStateParserTest {

    @Test
    @DisplayName("parse_noBlock_returnsInProgress")
    void parse_noBlock_returnsInProgress() {
        WorkflowNodeState state = WorkflowNodeStateParser.parse(
                "Some regular text without any state block");

        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());
        assertEquals("No state block found", state.getReason());
        assertTrue(state.getInteractions().isEmpty());
        assertNull(state.getArtifactTitle());
        assertNull(state.getRawBlock());
    }

    @Test
    @DisplayName("parse_emptyText_returnsInProgress")
    void parse_emptyText_returnsInProgress() {
        WorkflowNodeState state = WorkflowNodeStateParser.parse("");

        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());
        assertEquals("No state block found", state.getReason());

        WorkflowNodeState nullState = WorkflowNodeStateParser.parse(null);
        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, nullState.getStatus());
    }

    @Test
    @DisplayName("parse_readyToAdvance")
    void parse_readyToAdvance() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: READY_TO_ADVANCE
                reason: PRD completed
                artifact_title: 01-PRD.md
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.READY_TO_ADVANCE, state.getStatus());
        assertEquals("PRD completed", state.getReason());
        assertEquals("01-PRD.md", state.getArtifactTitle());
        assertTrue(state.getInteractions().isEmpty());
        assertNotNull(state.getRawBlock());
    }

    @Test
    @DisplayName("parse_inProgress")
    void parse_inProgress() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: IN_PROGRESS
                reason: Working on HLD design
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());
        assertEquals("Working on HLD design", state.getReason());
        assertNull(state.getArtifactTitle());
    }

    @Test
    @DisplayName("parse_needsUserInput_withSingleDecision")
    void parse_needsUserInput_withSingleDecision() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: NEEDS_USER_INPUT
                reason: 需要用户选择迁移方案
                interactions:
                  - id: HLD-UIP-001
                    type: DECISION
                    title: 选择迁移方案
                    question: 请选择推进方案
                    selection: single
                    options:
                      - id: A
                        label: 双写
                        description: 更安全
                        actionType: CHOOSE
                      - id: B
                        label: 直接切换
                        description: 风险较高
                        action_type: REJECT
                    allow_custom: true
                    required: true
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.NEEDS_USER_INPUT, state.getStatus());
        assertEquals("需要用户选择迁移方案", state.getReason());

        List<WorkflowNodeInteraction> interactions = state.getInteractions();
        assertEquals(1, interactions.size());

        WorkflowNodeInteraction interaction = interactions.get(0);
        assertEquals("HLD-UIP-001", interaction.getId());
        assertEquals(WorkflowNodeInteractionType.DECISION, interaction.getType());
        assertEquals("选择迁移方案", interaction.getTitle());
        assertEquals("请选择推进方案", interaction.getQuestion());
        assertEquals("single", interaction.getSelection());
        assertTrue(interaction.isAllowCustom());
        assertTrue(interaction.isRequired());

        List<WorkflowNodeInteraction.InteractionOption> options = interaction.getOptions();
        assertEquals(2, options.size());
	        assertEquals("A", options.get(0).getId());
	        assertEquals("双写", options.get(0).getLabel());
	        assertEquals("更安全", options.get(0).getDescription());
	        assertEquals("CHOOSE", options.get(0).getActionType());
	        assertEquals("B", options.get(1).getId());
	        assertEquals("直接切换", options.get(1).getLabel());
	        assertEquals("REJECT", options.get(1).getActionType());
	    }

    @Test
    @DisplayName("parse_needsUserInput_withMultipleInteractions")
    void parse_needsUserInput_withMultipleInteractions() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: NEEDS_USER_INPUT
                reason: 多个确认项
                interactions:
                  - id: INT-001
                    type: APPROVAL
                    title: 审批设计
                    question: 是否批准此设计？
                  - id: INT-002
                    type: ASK_USER
                    title: 补充信息
                    question: 请提供数据库连接信息
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.NEEDS_USER_INPUT, state.getStatus());
        List<WorkflowNodeInteraction> interactions = state.getInteractions();
        assertEquals(2, interactions.size());

        assertEquals("INT-001", interactions.get(0).getId());
        assertEquals(WorkflowNodeInteractionType.APPROVAL, interactions.get(0).getType());
        assertEquals("审批设计", interactions.get(0).getTitle());

        assertEquals("INT-002", interactions.get(1).getId());
        assertEquals(WorkflowNodeInteractionType.ASK_USER, interactions.get(1).getType());
        assertEquals("补充信息", interactions.get(1).getTitle());
    }

    @Test
    @DisplayName("parse_blocked")
    void parse_blocked() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: BLOCKED
                reason: 权限不足，无法访问生产环境
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.BLOCKED, state.getStatus());
        assertEquals("权限不足，无法访问生产环境", state.getReason());
    }

    @Test
    @DisplayName("parse_malformedBlock_returnsInProgress")
    void parse_malformedBlock_returnsInProgress() {
        String text = "<!-- AGENTCENTER_NODE_STATE\nthis is garbage {{}}\n-->";
        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);
        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());

        String text2 = "<!-- AGENTCENTER_NODE_STATE\nstatus: INVALID_STATUS\nreason: test\n-->";
        WorkflowNodeState state2 = WorkflowNodeStateParser.parse(text2);
        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state2.getStatus());

        String text3 = "<!-- AGENTCENTER_NODE_STATE\n---\nbroken: [yaml\n-->";
        WorkflowNodeState state3 = WorkflowNodeStateParser.parse(text3);
        assertNotNull(state3);
    }

    @Test
    @DisplayName("parse_blockWithExtraSpaces")
    void parse_blockWithExtraSpaces() {
        String text = "<!-- AGENTCENTER_NODE_STATE\n  status:   READY_TO_ADVANCE  \n  reason:   done   \n  artifact_title:   output.md  \n-->";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.READY_TO_ADVANCE, state.getStatus());
        assertEquals("done", state.getReason());
        assertEquals("output.md", state.getArtifactTitle());
    }

    @Test
    @DisplayName("parse_interactionsWithFields")
    void parse_interactionsWithFields() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: NEEDS_USER_INPUT
                reason: 需要配置信息
                interactions:
                  - id: CFG-001
                    type: INPUT
                    title: 数据库配置
                    question: 请填写数据库连接信息
                    fields:
                      - id: db_host
                        label: 主机地址
                        type: text
                        required: true
                      - id: db_port
                        label: 端口
                        type: number
                        required: true
                      - id: db_notes
                        label: 备注
                        type: textarea
                        required: false
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.NEEDS_USER_INPUT, state.getStatus());
        List<WorkflowNodeInteraction> interactions = state.getInteractions();
        assertEquals(1, interactions.size());

        WorkflowNodeInteraction interaction = interactions.get(0);
        assertEquals("CFG-001", interaction.getId());
        assertEquals(WorkflowNodeInteractionType.INPUT, interaction.getType());

        List<WorkflowNodeInteraction.InteractionField> fields = interaction.getFields();
        assertEquals(3, fields.size());

        assertEquals("db_host", fields.get(0).getId());
        assertEquals("主机地址", fields.get(0).getLabel());
        assertEquals("text", fields.get(0).getType());
        assertTrue(fields.get(0).isRequired());

        assertEquals("db_port", fields.get(1).getId());
        assertEquals("端口", fields.get(1).getLabel());
        assertEquals("number", fields.get(1).getType());

        assertEquals("db_notes", fields.get(2).getId());
        assertEquals("textarea", fields.get(2).getType());
	        assertFalse(fields.get(2).isRequired());
	    }

	    @Test
	    @DisplayName("parse_interactionsWithSelectableFieldMetadata")
	    void parse_interactionsWithSelectableFieldMetadata() {
	        String text = """
	                <!-- AGENTCENTER_NODE_STATE
	                status: NEEDS_USER_INPUT
	                reason: 需要选择范围
	                interactions:
	                  - id: PRD-SCOPE-FORM
	                    type: CUSTOM_FORM
	                    title: 选择 PRD 边界
	                    question: 请先选择常见边界，开放项再补充说明
	                    fields:
	                      - id: scope
	                        label: 范围边界
	                        type: select
	                        required: true
	                        placeholder: 请选择本次覆盖范围
	                        options:
	                          - value: workflow-interaction
	                            label: 工作流交互闭环
	                          - value: artifact-review
	                            label: 产物审阅闭环
	                      - id: include-risk
	                        label: 包含风险说明
	                        type: checkbox
	                        required: false
	                -->""";

	        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);
	        WorkflowNodeInteraction interaction = state.getInteractions().get(0);
	        List<WorkflowNodeInteraction.InteractionField> fields = interaction.getFields();

	        assertEquals(2, fields.size());
	        assertEquals("select", fields.get(0).getType());
	        assertEquals("请选择本次覆盖范围", fields.get(0).getPlaceholder());
	        assertEquals(2, fields.get(0).getOptions().size());
	        assertEquals("workflow-interaction", fields.get(0).getOptions().get(0).getValue());
	        assertEquals("工作流交互闭环", fields.get(0).getOptions().get(0).getLabel());
	        assertEquals("checkbox", fields.get(1).getType());
	    }

    @Test
    @DisplayName("parse_allowCustomFlag")
    void parse_allowCustomFlag() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: NEEDS_USER_INPUT
                reason: 选择方案
                interactions:
                  - id: DEC-001
                    type: DECISION
                    title: 选择
                    question: 请选择
                    allow_custom: true
                    required: false
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);
        WorkflowNodeInteraction interaction = state.getInteractions().get(0);

        assertTrue(interaction.isAllowCustom());
        assertFalse(interaction.isRequired());
    }

    @Test
    @DisplayName("parse_artifactTitle")
    void parse_artifactTitle() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: READY_TO_ADVANCE
                reason: HLD finished
                artifact_title: 02-HLD.md
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals("02-HLD.md", state.getArtifactTitle());
        assertEquals(WorkflowNodeStateStatus.READY_TO_ADVANCE, state.getStatus());
    }

    @Test
    @DisplayName("parse_textAroundBlock")
    void parse_textAroundBlock() {
        String text = """
                Here is some agent output before the block.

                The agent is working on the PRD document.

                <!-- AGENTCENTER_NODE_STATE
                status: READY_TO_ADVANCE
                reason: PRD complete
                artifact_title: 01-PRD.md
                -->

                Some text after the block as well.""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.READY_TO_ADVANCE, state.getStatus());
        assertEquals("PRD complete", state.getReason());
        assertEquals("01-PRD.md", state.getArtifactTitle());
    }

    @Test
    @DisplayName("parse_defaultInProgressFactory")
    void parse_defaultInProgressFactory() {
        WorkflowNodeState state = WorkflowNodeState.defaultInProgress();

        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());
        assertEquals("No state block found", state.getReason());
        assertNull(state.getArtifactTitle());
        assertTrue(state.getInteractions().isEmpty());
        assertNull(state.getRawBlock());
    }

    @Test
    @DisplayName("parse_blockedWithBlockerInteraction")
    void parse_blockedWithBlockerInteraction() {
        String text = """
                <!-- AGENTCENTER_NODE_STATE
                status: BLOCKED
                reason: API rate limit exceeded
                interactions:
                  - id: BLK-001
                    type: BLOCKER
                    title: API 限流
                    question: 请求频率超限，如何处理？
                    options:
                      - id: retry
                        label: 重试
                        description: 等待后重试
                      - id: skip
                        label: 跳过
                        description: 跳过此步骤
                      - id: cancel
                        label: 取消
                        description: 取消整个工作流
                -->""";

        WorkflowNodeState state = WorkflowNodeStateParser.parse(text);

        assertEquals(WorkflowNodeStateStatus.BLOCKED, state.getStatus());
        assertEquals("API rate limit exceeded", state.getReason());
        assertEquals(1, state.getInteractions().size());

        WorkflowNodeInteraction interaction = state.getInteractions().get(0);
        assertEquals(WorkflowNodeInteractionType.BLOCKER, interaction.getType());
        assertEquals(3, interaction.getOptions().size());
        assertEquals("retry", interaction.getOptions().get(0).getId());
        assertEquals("cancel", interaction.getOptions().get(2).getId());
    }

    @Test
    @DisplayName("parse_whitespaceOnlyText_returnsInProgress")
    void parse_whitespaceOnlyText_returnsInProgress() {
        WorkflowNodeState state = WorkflowNodeStateParser.parse("   \n  \t  \n  ");
        assertEquals(WorkflowNodeStateStatus.IN_PROGRESS, state.getStatus());
    }

    @Test
    @DisplayName("parse_allStatuses")
    void parse_allStatuses() {
        for (WorkflowNodeStateStatus status : WorkflowNodeStateStatus.values()) {
            String text = "<!-- AGENTCENTER_NODE_STATE\nstatus: " + status.name() + "\nreason: test\n-->";
            WorkflowNodeState state = WorkflowNodeStateParser.parse(text);
            assertEquals(status, state.getStatus(), "Failed for status: " + status);
        }
    }

    @Test
    @DisplayName("parse_allInteractionTypes")
    void parse_allInteractionTypes() {
        for (WorkflowNodeInteractionType type : WorkflowNodeInteractionType.values()) {
            String text = "<!-- AGENTCENTER_NODE_STATE\nstatus: NEEDS_USER_INPUT\nreason: test\ninteractions:\n  - id: T-001\n    type: "
                    + type.name() + "\n    title: Test\n    question: Q?\n-->";
            WorkflowNodeState state = WorkflowNodeStateParser.parse(text);
            assertEquals(1, state.getInteractions().size());
            assertEquals(type, state.getInteractions().get(0).getType(),
                    "Failed for type: " + type);
        }
    }

    @Test
    @DisplayName("state_equalityAndHashCode")
    void state_equalityAndHashCode() {
        WorkflowNodeState a = new WorkflowNodeState(
                WorkflowNodeStateStatus.IN_PROGRESS, "reason", null, List.of(), null);
        WorkflowNodeState b = new WorkflowNodeState(
                WorkflowNodeStateStatus.IN_PROGRESS, "reason", null, List.of(), null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("state_toString")
    void state_toString() {
        WorkflowNodeState state = new WorkflowNodeState(
                WorkflowNodeStateStatus.READY_TO_ADVANCE, "done", "file.md", List.of(), null);

        String str = state.toString();
        assertTrue(str.contains("READY_TO_ADVANCE"));
        assertTrue(str.contains("done"));
        assertTrue(str.contains("file.md"));
    }

    @Test
    @DisplayName("stripStateBlock_withBlock_removesBlock")
    void stripStateBlock_withBlock_removesBlock() {
        String text = """
                Some content before.

                <!-- AGENTCENTER_NODE_STATE
                status: READY_TO_ADVANCE
                reason: PRD completed
                artifact_title: 01-PRD.md
                -->

                Some content after.""";;

        String result = WorkflowNodeStateParser.stripStateBlock(text);

        assertFalse(result.contains("AGENTCENTER_NODE_STATE"));
        assertTrue(result.contains("Some content before."));
        assertTrue(result.contains("Some content after."));
    }

    @Test
    @DisplayName("stripStateBlock_withoutBlock_unchanged")
    void stripStateBlock_withoutBlock_unchanged() {
        String text = "Just regular text without any state block.";
        String result = WorkflowNodeStateParser.stripStateBlock(text);
        assertEquals(text, result);
    }

    @Test
    @DisplayName("stripStateBlock_null_returnsNull")
    void stripStateBlock_null_returnsNull() {
        assertNull(WorkflowNodeStateParser.stripStateBlock(null));
    }

    @Test
    @DisplayName("stripStateBlock_empty_returnsEmpty")
    void stripStateBlock_empty_returnsEmpty() {
        assertEquals("", WorkflowNodeStateParser.stripStateBlock(""));
    }

    @Test
    @DisplayName("stripStateBlock_blank_returnsBlank")
    void stripStateBlock_blank_returnsBlank() {
        assertEquals("   ", WorkflowNodeStateParser.stripStateBlock("   "));
    }
}
