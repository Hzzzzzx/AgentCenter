package com.agentcenter.bridge.application.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowPromptComposerTest {

    private WorkflowPromptComposer composer;
    private WorkflowNodeInstructionComposer instructionComposer;

    @BeforeEach
    void setUp() {
        instructionComposer = new WorkflowNodeInstructionComposer();
        composer = new WorkflowPromptComposer(instructionComposer);
    }

    private NodeExecutionContext basicContext() {
        return new NodeExecutionContext(
                new NodeExecutionContext.WorkItemData(
                        "WI-001", "CODE-001", "实现登录功能", "STORY",
                        "IN_PROGRESS", "HIGH", "实现用户登录认证流程",
                        "PROJ-001", "SPACE-001", "ITER-001", "USER-001"
                ),
                new NodeExecutionContext.NodeData(
                        "NODE-001", "需求分析", "req-analysis", "ANALYSIS",
                        "requirement-analyst", "ALL", "MARKDOWN",
                        true, "产出完整需求文档"
                ),
                null,
                List.of(),
                null
        );
    }

    @Nested
    @DisplayName("composeNodePrompt")
    class ComposeNodePrompt {

        @Test
        @DisplayName("prompt includes work item fields")
        void composeNodePrompt_basicWorkItem() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertTrue(prompt.contains("# 工作流节点执行输入"), "should have header");
            assertTrue(prompt.contains("## 工作项"), "should have work item section");
            assertTrue(prompt.contains("- ID：WI-001"), "should have ID");
            assertTrue(prompt.contains("- 编号：CODE-001"), "should have code");
            assertTrue(prompt.contains("- 标题：实现登录功能"), "should have title");
            assertTrue(prompt.contains("- 类型：STORY"), "should have type");
            assertTrue(prompt.contains("- 状态：IN_PROGRESS"), "should have status");
            assertTrue(prompt.contains("- 优先级：HIGH"), "should have priority");
            assertTrue(prompt.contains("实现用户登录认证流程"), "should have description content");
        }

        @Test
        @DisplayName("prompt includes upstream artifact content")
        void composeNodePrompt_withUpstreamArtifact() {
            NodeExecutionContext.UpstreamArtifactData artifact = new NodeExecutionContext.UpstreamArtifactData(
                    "ART-001", "上游需求文档", "# 需求内容\n功能列表...",
                    "MARKDOWN", "NODE-000"
            );
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(), basicContext().getNode(),
                    artifact, List.of(), null
            );

            String prompt = composer.composeNodePrompt(context);

            assertTrue(prompt.contains("## 上游产物"), "should have upstream section");
            assertTrue(prompt.contains("- artifactId：ART-001"), "should have artifact ID");
            assertTrue(prompt.contains("- title：上游需求文档"), "should have artifact title");
            assertTrue(prompt.contains("- type：MARKDOWN"), "should have artifact type");
            assertTrue(prompt.contains("- sourceNodeInstanceId：NODE-000"), "should have source node");
            assertTrue(prompt.contains("# 需求内容"), "should have artifact content");
            assertTrue(prompt.contains("功能列表..."), "should have artifact body");
        }

        @Test
        @DisplayName("prompt shows '无' when no upstream artifact")
        void composeNodePrompt_withoutUpstreamArtifact() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertTrue(prompt.contains("## 上游产物"), "should have upstream section header");
            assertTrue(prompt.contains("无。该节点应基于工作项本身生成结果。"), "should indicate no artifact");
        }

        @Test
        @DisplayName("prompt shows '无' when upstream artifact has null content")
        void composeNodePrompt_withUpstreamArtifactNullContent() {
            NodeExecutionContext.UpstreamArtifactData artifact = new NodeExecutionContext.UpstreamArtifactData(
                    "ART-001", "title", null, "MARKDOWN", "NODE-000"
            );
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(), basicContext().getNode(),
                    artifact, List.of(), null
            );

            String prompt = composer.composeNodePrompt(context);

            assertTrue(prompt.contains("无。该节点应基于工作项本身生成结果。"), "null content treated as no artifact");
        }

        @Test
        @DisplayName("prompt includes resolved interactions")
        void composeNodePrompt_withInteractionHistory() {
            NodeExecutionContext.ResolvedInteraction interaction = new NodeExecutionContext.ResolvedInteraction(
                    "CONF-001", "确认技术方案", "请选择数据库方案",
                    "DECISION", "{\"selected\":\"PostgreSQL\"}", "建议使用PostgreSQL"
            );
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(), basicContext().getNode(),
                    null, List.of(interaction), null
            );

            String prompt = composer.composeNodePrompt(context);

            assertTrue(prompt.contains("## 用户交互回答历史"), "should have interaction section");
            assertTrue(prompt.contains("- 确认项：确认技术方案"), "should have title");
            assertTrue(prompt.contains("- 原始问题：请选择数据库方案"), "should have question");
            assertTrue(prompt.contains("- 交互类型：DECISION"), "should have type");
            assertTrue(prompt.contains("- 用户处理：{\"selected\":\"PostgreSQL\"}"), "should have payload");
            assertTrue(prompt.contains("- 用户备注：建议使用PostgreSQL"), "should have comment");
        }

        @Test
        @DisplayName("prompt omits interaction section when empty")
        void composeNodePrompt_withoutInteractionHistory() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertFalse(prompt.contains("## 用户交互回答历史"), "should not have interaction section when empty");
        }

        @Test
        @DisplayName("prompt includes supplemental user input")
        void composeNodePrompt_withSupplementalInput() {
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(), basicContext().getNode(),
                    null, List.of(), "用户要求使用JWT认证"
            );

            String prompt = composer.composeNodePrompt(context);

            assertTrue(prompt.contains("## 用户本轮输入（优先执行）"), "should have supplemental section");
            assertTrue(prompt.contains("当前节点的直接指令"), "should mark user input as direct instruction");
            assertTrue(prompt.contains("用户要求使用JWT认证"), "should have input content");
        }

        @Test
        @DisplayName("prompt omits supplemental section when null")
        void composeNodePrompt_withoutSupplementalInput() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertFalse(prompt.contains("## 用户本轮输入（优先执行）"), "should not have supplemental section when null");
        }

        @Test
        @DisplayName("prompt omits supplemental section when blank")
        void composeNodePrompt_withBlankSupplementalInput() {
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(), basicContext().getNode(),
                    null, List.of(), "   "
            );

            String prompt = composer.composeNodePrompt(context);

            assertFalse(prompt.contains("## 用户本轮输入（优先执行）"), "should not have supplemental section when blank");
        }

        @Test
        @DisplayName("prompt ends with AGENTCENTER_NODE_STATE protocol instruction")
        void composeNodePrompt_includesProtocolInstruction() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertTrue(prompt.contains("AGENTCENTER_NODE_STATE"), "should have protocol marker");
            assertTrue(prompt.contains("## AgentCenter 节点状态协议"), "should have protocol header");
        }

        @Test
        @DisplayName("node section includes all fields")
        void composeNodePrompt_nodeFields() {
            String prompt = composer.composeNodePrompt(basicContext());

            assertTrue(prompt.contains("## 当前节点"), "should have node section");
            assertTrue(prompt.contains("- 节点ID：NODE-001"), "should have node ID");
            assertTrue(prompt.contains("- 节点名称：需求分析"), "should have node name");
            assertTrue(prompt.contains("- 节点Key：req-analysis"), "should have node key");
            assertTrue(prompt.contains("- 阶段Key：ANALYSIS"), "should have stage key");
            assertTrue(prompt.contains("- Skill：requirement-analyst"), "should have skill name");
            assertTrue(prompt.contains("- 输入策略：ALL"), "should have input policy");
            assertTrue(prompt.contains("- 输出类型：MARKDOWN"), "should have output type");
            assertTrue(prompt.contains("- 需要确认：是"), "should show confirmation required");
            assertTrue(prompt.contains("- 阶段目标：产出完整需求文档"), "should have stage goal");
        }

        @Test
        @DisplayName("node section omits stage goal when null")
        void composeNodePrompt_nodeWithoutStageGoal() {
            NodeExecutionContext context = new NodeExecutionContext(
                    basicContext().getWorkItem(),
                    new NodeExecutionContext.NodeData(
                            "NODE-001", "需求分析", "req-analysis", "ANALYSIS",
                            "requirement-analyst", "ALL", "MARKDOWN", true, null
                    ),
                    null, List.of(), null
            );

            String prompt = composer.composeNodePrompt(context);

            assertFalse(prompt.contains("阶段目标"), "should not have stage goal when null");
        }

        @Test
        @DisplayName("work item shows fallback for null description")
        void composeNodePrompt_nullDescription() {
            NodeExecutionContext context = new NodeExecutionContext(
                    new NodeExecutionContext.WorkItemData(
                            "WI-001", "CODE-001", "标题", "STORY",
                            "IN_PROGRESS", "HIGH", null,
                            "PROJ-001", "SPACE-001", "ITER-001", "USER-001"
                    ),
                    basicContext().getNode(), null, List.of(), null
            );

            String prompt = composer.composeNodePrompt(context);

            assertTrue(prompt.contains("暂无描述"), "should show fallback for null description");
        }
    }

    @Nested
    @DisplayName("composeInvocationRequest")
    class ComposeInvocationRequest {

        @Test
        @DisplayName("injects resume state and subagent policy into user prompt")
        void composeInvocationRequest_withResumeState() {
            WorkflowResumeState resumeState = new WorkflowResumeState(
                    "wf-1",
                    "node-2",
                    "wi-1",
                    "project-1",
                    "runtime-1",
                    "PENDING_USER_INTERACTION",
                    "RUNNING",
                    "hld-design",
                    "invoke-1",
                    List.of(
                            new WorkflowResumeState.WorkflowStep(
                                    "node-1", "def-1", "requirement_refine", "需求整理 (PRD)",
                                    1, "prd-design", "PRD", "COMPLETED", false),
                            new WorkflowResumeState.WorkflowStep(
                                    "node-2", "def-2", "solution_design", "方案设计 (HLD)",
                                    2, "hld-design", "HLD", "RUNNING", true)
                    ),
                    List.of(new WorkflowResumeState.PendingInteraction(
                            "conf-1", "DECISION", "方案选择"))
            );

            SkillInvocationRequest request = composer.composeInvocationRequest(
                    "hld-design", "# 输入上下文", resumeState);

            assertTrue(request.userPrompt().contains("## AGENTCENTER_RESUME_STATE"));
            assertTrue(request.userPrompt().contains("workflowInstanceId：wf-1"));
            assertTrue(request.userPrompt().contains("[CURRENT] 2. 方案设计 (HLD)"));
            assertTrue(request.userPrompt().contains("当前仍有待处理交互，禁止输出 READY_TO_ADVANCE"));
            assertTrue(request.userPrompt().contains("可以使用 OpenCode 子 Agent"));
            assertTrue(request.userPrompt().contains("子 Agent 只能返回 SUBTASK_RESULT"));
            assertTrue(request.instructionPrompt().contains("AGENTCENTER_NODE_STATE"));
        }
    }

    @Nested
    @DisplayName("protocol instruction")
    class ProtocolInstruction {

        @Test
        @DisplayName("instruction mentions all 4 statuses")
        void instructionComposer_containsAllStatuses() {
            String instruction = instructionComposer.composeProtocolInstruction();

            assertTrue(instruction.contains("IN_PROGRESS"), "should mention IN_PROGRESS");
            assertTrue(instruction.contains("NEEDS_USER_INPUT"), "should mention NEEDS_USER_INPUT");
            assertTrue(instruction.contains("READY_TO_ADVANCE"), "should mention READY_TO_ADVANCE");
            assertTrue(instruction.contains("BLOCKED"), "should mention BLOCKED");
        }

        @Test
        @DisplayName("instruction contains interaction example")
        void instructionComposer_containsInteractionExample() {
            String instruction = instructionComposer.composeProtocolInstruction();

            assertTrue(instruction.contains("interactions:"), "should show interaction syntax");
            assertTrue(instruction.contains("type: DECISION"), "should show DECISION type");
            assertTrue(instruction.contains("selection: single"), "should show selection field");
            assertTrue(instruction.contains("options:"), "should show options block");
            assertTrue(instruction.contains("allow_custom:"), "should show allow_custom");
        }

        @Test
        @DisplayName("instruction contains all interaction types")
        void instructionComposer_containsAllInteractionTypes() {
            String instruction = instructionComposer.composeProtocolInstruction();

            assertTrue(instruction.contains("ASK_USER"), "should mention ASK_USER");
            assertTrue(instruction.contains("INPUT"), "should mention INPUT");
            assertTrue(instruction.contains("DECISION"), "should mention DECISION");
            assertTrue(instruction.contains("APPROVAL"), "should mention APPROVAL");
            assertTrue(instruction.contains("ARTIFACT_REVIEW"), "should mention ARTIFACT_REVIEW");
            assertTrue(instruction.contains("PERMISSION"), "should mention PERMISSION");
            assertTrue(instruction.contains("CUSTOM_FORM"), "should mention CUSTOM_FORM");
            assertTrue(instruction.contains("RANKING"), "should mention RANKING");
            assertTrue(instruction.contains("SCALE"), "should mention SCALE");
            assertTrue(instruction.contains("BLOCKER"), "should mention BLOCKER");
        }

        @Test
        @DisplayName("instruction contains important rules")
        void instructionComposer_containsImportantRules() {
            String instruction = instructionComposer.composeProtocolInstruction();

            assertTrue(instruction.contains("如果你不输出状态块，平台默认视为 IN_PROGRESS"), "should explain default");
            assertTrue(instruction.contains("优先使用 OpenCode 原生 Question 交互"), "should prefer OpenCode question");
            assertTrue(instruction.contains("AgentCenter Bridge 会将 Question 翻译为平台待确认"), "should explain question bridge");
            assertTrue(instruction.contains("只有 READY_TO_ADVANCE 才会保存"), "should explain advance rule");
            assertTrue(instruction.contains("NEEDS_USER_INPUT 下用户回答后"), "should explain feedback loop");
            assertTrue(instruction.contains("自然多轮输入"), "should preserve flexible user interaction");
            assertTrue(instruction.contains("用户可以随时在页面输入补充、调整、继续或接管指令"), "should mention page-side intervention");
            assertTrue(instruction.contains("不要把可执行的用户输入回复成“等待系统推进”"), "should avoid rigid system-progress wording");
            assertTrue(instruction.contains("由系统继续推进"), "should warn against placeholder advance wording");
            assertTrue(instruction.contains("type: DECISION"), "should require structured decision choices");
            assertTrue(instruction.contains("2-3 个互斥选择"), "should prefer options for finite choices");
        }
    }

    @Nested
    @DisplayName("WorkflowNodeInstructionComposer standalone")
    class InstructionComposerStandalone {

        @Test
        @DisplayName("returns non-empty instruction")
        void instructionComposer_returnsNonEmpty() {
            String instruction = instructionComposer.composeProtocolInstruction();

            assertNotNull(instruction, "instruction should not be null");
            assertFalse(instruction.isBlank(), "instruction should not be blank");
            assertTrue(instruction.length() > 100, "instruction should be substantial");
        }
    }
}
