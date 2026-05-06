package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentcenter.bridge.infrastructure.runtime.mock.MockRuntimeAdapter;

class RuntimeResourceServiceTest {

    @TempDir
    Path projectRoot;

    @Test
    void refreshSkillsCreatesSkillsDirectoryWhenMissing() {
        RuntimeResourceService service = new RuntimeResourceService(
                new MockRuntimeAdapter(),
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
                new MockRuntimeAdapter(),
                projectRoot.toString()
        );

        var response = service.refreshSkills();

        assertThat(response.skillCount()).isEqualTo(1);
        assertThat(response.skills().get(0).name()).isEqualTo("fe-requirement-refine");
        assertThat(response.skills().get(0).description()).isEqualTo("将 FE 需求整理成设计文档");
        assertThat(response.skills().get(0).relativePath()).isEqualTo(".opencode/skills/fe-requirement-refine");
    }
}
