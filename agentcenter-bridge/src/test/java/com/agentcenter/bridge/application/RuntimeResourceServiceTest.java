package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentcenter.bridge.application.runtime.RuntimeCapabilities;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.RuntimeDescriptor;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

class RuntimeResourceServiceTest {

    private static final RuntimeGateway STUB_GATEWAY = new RuntimeGateway() {
        @Override public String createSession(RuntimeType rt, String workItemId, String agentSessionId) { return "stub-session"; }
        @Override public String ensureSession(RuntimeType rt, String workItemId, String agentSessionId, String runtimeSessionId) { return runtimeSessionId != null ? runtimeSessionId : "stub-session"; }
        @Override public SkillRunResult runSkill(RuntimeType rt, String sessionId, String skillName, String inputContext) {
            return new SkillRunResult(true, "stub output", "MARKDOWN", null, true);
        }
        @Override public void sendMessage(RuntimeType rt, String sessionId, String userMessage) {}
        @Override public void cancel(RuntimeType rt, String sessionId) {}
        @Override public void refreshSkills(RuntimeType rt, RuntimeSkillSnapshot snapshot) {}
        @Override public void refreshMcps(RuntimeType rt) {}
        @Override public RuntimeDescriptor describe(RuntimeType rt) { return new RuntimeDescriptor("Stub", "TEST", "test stub", capabilities(rt)); }
        @Override public RuntimeCapabilities capabilities(RuntimeType rt) { return new RuntimeCapabilities(true, true, true, true); }
    };

    @TempDir
    Path projectRoot;

    @Test
    void refreshSkillsCreatesSkillsDirectoryWhenMissing() {
        RuntimeResourceService service = new RuntimeResourceService(
                STUB_GATEWAY,
                projectRoot.toString()
        );

        var response = service.refreshSkills();

        assertThat(response.skillCount()).isZero();
        assertThat(Files.isDirectory(projectRoot.resolve(".opencode/skills"))).isTrue();
    }

    @Test
    void refreshSkillsScansLocalSkillDirectories() throws Exception {
        Path skillDir = projectRoot.resolve(".opencode/skills/fe-requirement-refine");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: fe-requirement-refine
                description: 将 FE 需求整理成设计文档
                ---

                使用当前工作项上下文生成 Markdown。
                """);
        RuntimeResourceService service = new RuntimeResourceService(
                STUB_GATEWAY,
                projectRoot.toString()
        );

        var response = service.refreshSkills();

        assertThat(response.skillCount()).isEqualTo(1);
        assertThat(response.skills().get(0).name()).isEqualTo("fe-requirement-refine");
        assertThat(response.skills().get(0).description()).isEqualTo("将 FE 需求整理成设计文档");
        assertThat(response.skills().get(0).relativePath()).isEqualTo(".opencode/skills/fe-requirement-refine");
    }
}
