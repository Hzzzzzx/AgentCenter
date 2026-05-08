package com.agentcenter.bridge.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.AbstractExecutorService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.agentcenter.bridge.application.runtime.*;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

@TestConfiguration
public class TestWorkflowExecutorConfig {

    public static final List<String> CAPTURED_SKILL_NAMES = new CopyOnWriteArrayList<>();
    public static final List<String> CAPTURED_INPUT_CONTEXTS = new CopyOnWriteArrayList<>();

    public static void clearCapturedRuntimeInputs() {
        CAPTURED_SKILL_NAMES.clear();
        CAPTURED_INPUT_CONTEXTS.clear();
    }

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
    public RuntimeGateway stubRuntimeGateway() {
        return new RuntimeGateway() {
            @Override
            public String createSession(RuntimeType rt, String workItemId, String agentSessionId) {
                return "stub-session-" + (workItemId != null ? workItemId : "test");
            }

            @Override
            public String ensureSession(RuntimeType rt, String workItemId, String agentSessionId, String runtimeSessionId) {
                return runtimeSessionId != null ? runtimeSessionId : "stub-session-" + (workItemId != null ? workItemId : "test");
            }

            @Override
            public SkillRunResult runSkill(RuntimeType rt, String sessionId, String skillName, String inputContext) {
                CAPTURED_SKILL_NAMES.add(skillName);
                CAPTURED_INPUT_CONTEXTS.add(inputContext);
                String output = switch (skillName) {
                    case "fe.requirement.refine", "prd-desingn" -> """
                            # PRD

                            测试 PRD 输出

                            | 交互点 | 类型 | 是否触发 | 选项 | 触发条件 | 建议问题/动作 | 默认处理 |
                            | --- | --- | --- | --- | --- | --- | --- |
                            | 需求澄清 | ASK_USER | 否 |  | 信息完整 | 无需用户补充 | 自动进入 HLD |
                            """.trim();
                    case "fe.solution.design", "hld-design" -> """
                            # HLD

                            测试 HLD 输出

                            | 交互点 | 类型 | 是否触发 | 选项 | 触发条件 | 建议问题/动作 | 默认处理 |
                            | --- | --- | --- | --- | --- | --- | --- |
                            | 方案选择 | DECISION_REQUIRED | 是 | 低风险方案 / 低成本方案 / 完整方案 | 存在多个成本/风险差异明显的方案 | 请用户选择推荐方案 | 默认选择低风险方案 |
                            """.trim();
                    case "fe.implementation.plan", "lld-design" -> "# LLD\n\n测试 LLD 输出";
                    default -> "# %s 输出\n\n测试节点输出".formatted(skillName);
                };
                return new SkillRunResult(true, output, "MARKDOWN", null, true);
            }

            @Override
            public void sendMessage(RuntimeType rt, String sessionId, String userMessage) {}

            @Override
            public void cancel(RuntimeType rt, String sessionId) {}

            @Override
            public void refreshSkills(RuntimeType rt, RuntimeSkillSnapshot snapshot) {}

            @Override
            public void refreshMcps(RuntimeType rt) {}

            @Override
            public RuntimeDescriptor describe(RuntimeType rt) {
                return new RuntimeDescriptor("Stub", "TEST", "test stub", capabilities(rt));
            }

            @Override
            public RuntimeCapabilities capabilities(RuntimeType rt) {
                return new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false);
            }

            @Override
            public java.util.List<com.agentcenter.bridge.api.dto.RuntimeSkillDto> scanSkills(RuntimeType rt) { return java.util.List.of(); }
            @Override
            public String installSkill(RuntimeType rt, String name, java.nio.file.Path dir) { return ".opencode/skills/" + name; }
            @Override
            public void deleteSkillFiles(RuntimeType rt, String rel, String name) {}
            @Override
            public String getSkillsRootPath(RuntimeType rt) { return "/tmp/test/.opencode/skills"; }
            @Override
            public java.util.Map<String, Object> readMcpConfig(RuntimeType rt) { return java.util.Map.of(); }
            @Override
            public void writeMcpConfig(RuntimeType rt, java.util.Map<String, Object> config) {}
        };
    }
}
