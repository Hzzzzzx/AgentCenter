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
            - 如果你不输出状态块，系统默认视为 IN_PROGRESS，不会推进。
            - 只有 READY_TO_ADVANCE 才会保存最终 artifact 并允许进入下一节点。
            - NEEDS_USER_INPUT 下用户回答后，系统会将回答作为新输入回灌给你继续执行。
            """;
}
