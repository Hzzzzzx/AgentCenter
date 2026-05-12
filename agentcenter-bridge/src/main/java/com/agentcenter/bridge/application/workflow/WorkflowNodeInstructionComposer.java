package com.agentcenter.bridge.application.workflow;

/**
 * Generates runtime-agnostic node execution protocol instructions.
 * These instructions tell the Agent how to output the AGENTCENTER_NODE_STATE block.
 */
public class WorkflowNodeInstructionComposer {

    /**
     * Generates the full protocol instruction text to include in the Skill prompt.
     * This tells the Agent about the available states and how to format them.
     */
    public String composeProtocolInstruction() {
        return PROTOCOL_INSTRUCTION;
    }

    private static final String PROTOCOL_INSTRUCTION = """
            
            ---
            
            ## AgentCenter 节点状态协议
            
            你正在 AgentCenter 工作流中执行一个节点。请在每次回复的末尾输出一个状态块，告知系统你的当前状态。
            
            格式：
            ```markdown
            <!-- AGENTCENTER_NODE_STATE
            status: STATUS_VALUE
            reason: 一句话说明当前状态原因
            -->
            ```
            
            可选状态：
            - `IN_PROGRESS`：阶段性输出，还未完成。系统不会推进到下一节点。
            - `NEEDS_USER_INPUT`：需要用户补充、选择、确认或授权。系统会创建交互请求等待用户回答。
            - `READY_TO_ADVANCE`：当前 Skill 已完成，输入输出充分。系统会保存最终 artifact 并创建推进确认项，等待用户选择后再进入下一节点。
            - `BLOCKED`：遇到工具、权限、信息或运行时异常，无法继续。系统会创建异常交互。
            
            如果需要用户输入，可以在状态块中声明交互：
            ```markdown
            <!-- AGENTCENTER_NODE_STATE
            status: NEEDS_USER_INPUT
            reason: 需要用户选择方案
            interactions:
              - id: UIP-001
                type: DECISION
                title: 选择方案
                question: 请选择推进方案
                selection: single
                options:
                  - id: A
                    label: 方案A
                    description: 描述A
                  - id: B
                    label: 方案B
                    description: 描述B
                allow_custom: true
                required: true
            -->
            ```
            
            交互类型：ASK_USER / INPUT / DECISION / APPROVAL / ARTIFACT_REVIEW / PERMISSION / CUSTOM_FORM / RANKING / SCALE / BLOCKER
            
            **重要**：
            - 在 OpenCode Runtime 中，需要用户澄清、选择、确认或授权时，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。
            - 当不能使用 OpenCode Question，或需要明确声明节点完成/阻塞状态时，再使用本 `AGENTCENTER_NODE_STATE` 协议。
            - 如果你要让用户在有限方案中选择，必须在 `interactions` 中输出 `type: DECISION` 和 `options`；不要只写 `NEEDS_USER_INPUT` 的 `reason`。
            - 只有开放式补充信息才使用 `INPUT` / `ASK_USER`；能枚举 2-3 个互斥选择时优先给选项。
            - 如果你不输出状态块，系统默认视为 IN_PROGRESS，不会推进。
            - 只有 READY_TO_ADVANCE 才会保存最终 artifact 并允许进入下一节点。
            - NEEDS_USER_INPUT 下用户回答后，系统会将回答作为新输入回灌给你继续执行。
            - 用户说“继续”、补充、调整或追问时，优先把它当作当前 Skill 的自然多轮输入，直接继续产出、修正或提问。
            - 状态块只服务系统编排；面向用户的正文不要只输出“可在适当时机推进”这类流程占位话术。
            """;
}
