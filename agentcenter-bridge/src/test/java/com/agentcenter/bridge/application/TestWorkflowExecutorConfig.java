package com.agentcenter.bridge.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.AbstractExecutorService;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.SkillRunResult;

@TestConfiguration
public class TestWorkflowExecutorConfig {

    @Bean
    @Primary
    @Qualifier("workflowExecutor")
    public ExecutorService synchronousWorkflowExecutor() {
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {}

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
    }

    @Bean
    @Primary
    public AgentRuntimeAdapter stubRuntimeAdapter() {
        return new AgentRuntimeAdapter() {
            @Override
            public String createSession(String workItemId, String agentSessionId) {
                return "stub-session-" + (workItemId != null ? workItemId : "test");
            }

            @Override
            public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
                String output = switch (skillName) {
                    case "fe.requirement.refine", "prd-desingn" -> "# PRD\n\n测试 PRD 输出";
                    case "fe.solution.design", "hld-design" -> "# HLD\n\n测试 HLD 输出";
                    case "fe.implementation.plan", "lld-design" -> "# LLD\n\n测试 LLD 输出";
                    default -> "# %s 输出\n\n测试节点输出".formatted(skillName);
                };
                return new SkillRunResult(true, output, "MARKDOWN", null, true);
            }

            @Override
            public void sendMessage(String sessionId, String userMessage) {
                // stub: no-op
            }

            @Override
            public void cancel(String sessionId) {
                // stub: no-op
            }
        };
    }
}
