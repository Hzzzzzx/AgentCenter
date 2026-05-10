package com.agentcenter.bridge.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.AbstractExecutorService;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final AtomicReference<String> CUSTOM_SKILL_OUTPUT = new AtomicReference<>();
    private static final AtomicReference<SkillRunResult> CUSTOM_SKILL_RESULT = new AtomicReference<>();
    private static final AtomicReference<RuntimeException> ENSURE_SESSION_ERROR = new AtomicReference<>();
    private static final ConcurrentHashMap<String, String> SKILL_OUTPUT_OVERRIDES = new ConcurrentHashMap<>();

    public static void clearCapturedRuntimeInputs() {
        CAPTURED_SKILL_NAMES.clear();
        CAPTURED_INPUT_CONTEXTS.clear();
        CUSTOM_SKILL_OUTPUT.set(null);
        CUSTOM_SKILL_RESULT.set(null);
        ENSURE_SESSION_ERROR.set(null);
        SKILL_OUTPUT_OVERRIDES.clear();
    }

    public static void setNextSkillOutput(String output) {
        CUSTOM_SKILL_OUTPUT.set(output);
    }

    public static void clearCustomSkillOutput() {
        CUSTOM_SKILL_OUTPUT.set(null);
    }

    public static void setNextSkillResult(SkillRunResult result) {
        CUSTOM_SKILL_RESULT.set(result);
    }

    public static void clearCustomSkillResult() {
        CUSTOM_SKILL_RESULT.set(null);
    }

    public static void setSkillOutputForName(String skillName, String output) {
        SKILL_OUTPUT_OVERRIDES.put(skillName, output);
    }

    public static void clearSkillOutputOverrides() {
        SKILL_OUTPUT_OVERRIDES.clear();
    }

    public static void setEnsureSessionError(RuntimeException error) {
        ENSURE_SESSION_ERROR.set(error);
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
                RuntimeException error = ENSURE_SESSION_ERROR.get();
                if (error != null) {
                    throw error;
                }
                return runtimeSessionId != null ? runtimeSessionId : "stub-session-" + (workItemId != null ? workItemId : "test");
            }

            @Override
            public SkillRunResult runSkill(RuntimeType rt, String sessionId, String skillName, String inputContext) {
                CAPTURED_SKILL_NAMES.add(skillName);
                CAPTURED_INPUT_CONTEXTS.add(inputContext);

                SkillRunResult customResult = CUSTOM_SKILL_RESULT.getAndSet(null);
                if (customResult != null) {
                    return customResult;
                }

                String customOutput = CUSTOM_SKILL_OUTPUT.getAndSet(null);
                if (customOutput != null) {
                    return new SkillRunResult(true, customOutput, "MARKDOWN", null, true);
                }

                String namedOverride = SKILL_OUTPUT_OVERRIDES.get(skillName);
                if (namedOverride != null) {
                    return new SkillRunResult(true, namedOverride, "MARKDOWN", null, true);
                }

                String output = switch (skillName) {
                    case "fe.requirement.refine", "prd-desingn" -> """
                            # PRD

                            测试 PRD 输出

                            <!-- AGENTCENTER_NODE_STATE
                            status: READY_TO_ADVANCE
                            reason: PRD complete
                            artifact_title: FE1234-需求整理 (PRD).md
                            -->
                            """.trim();
                    case "fe.solution.design", "hld-design" -> """
                            # HLD

                            测试 HLD 输出

                            <!-- AGENTCENTER_NODE_STATE
                            status: NEEDS_USER_INPUT
                            reason: 存在多个成本/风险差异明显的方案，需要用户选择
                            interactions:
                              - id: hld-decision-1
                                type: DECISION
                                title: FE1234 方案设计 · 方案选择
                                question: 请选择推荐方案
                                options:
                                  - id: opt-1
                                    label: 低风险方案
                                  - id: opt-2
                                    label: 低成本方案
                                  - id: opt-3
                                    label: 完整方案
                            -->
                            """.trim();
                    case "fe.implementation.plan", "lld-design" -> """
                            # LLD

                            测试 LLD 输出

                            <!-- AGENTCENTER_NODE_STATE
                            status: READY_TO_ADVANCE
                            reason: LLD complete
                            -->
                            """.trim();
                    default -> """
                            # %s 输出

                            测试节点输出

                            <!-- AGENTCENTER_NODE_STATE
                            status: READY_TO_ADVANCE
                            reason: Default complete
                            -->
                            """.formatted(skillName).trim();
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

            @Override
            public void registerWorkflowNodeContext(RuntimeType rt, String agentSessionId, String workItemId,
                                                      String workflowInstanceId, String workflowNodeInstanceId) {}
        };
    }
}
