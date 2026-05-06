package com.agentcenter.bridge.infrastructure.runtime.mock;

import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agentcenter.runtime.opencode.serve.enabled", havingValue = "false", matchIfMissing = true)
public class MockRuntimeAdapter implements AgentRuntimeAdapter {

    @Override
    public String createSession(String workItemId, String agentSessionId) {
        if (agentSessionId != null && !agentSessionId.isBlank()) {
            return agentSessionId;
        }
        return "mock-session-" + workItemId;
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        String output = generateMockOutput(skillName, inputContext);
        return new SkillRunResult(true, output, "MARKDOWN", null, true);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        // Mock: no-op
    }

    @Override
    public void cancel(String sessionId) {
        // Mock: no-op
    }

    private String generateMockOutput(String skillName, String inputContext) {
        return switch (skillName) {
            case "fe.requirement.refine" -> """
                # 需求设计文档

                ## 概述
                本文档对需求进行整理和完善。

                ## 功能需求
                1. 核心功能描述
                2. 用户交互流程
                3. 数据模型要求

                ## 非功能需求
                - 性能要求
                - 安全要求
                - 可用性要求

                ## 验收标准
                - [ ] 标准一
                - [ ] 标准二

                > 此文档由 Mock Runtime 生成，用于演示工作流节点输出。
                """;
            case "fe.solution.design" -> """
                # 方案设计文档

                ## 技术方案
                采用 Spring Boot 3 + Vue 3 的前后端分离架构。

                ## 接口设计
                RESTful API，遵循 OpenAPI 规范。

                ## 数据模型
                使用 MyBatis + SQLite 持久化。

                > 此文档由 Mock Runtime 生成。
                """;
            default -> """
                # %s 输出

                此节点由 Mock Runtime 执行完成。

                > Mock output for skill: %s
                """.formatted(skillName, skillName);
        };
    }
}
