package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
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
        @Override public RuntimeCapabilities capabilities(RuntimeType rt) { return new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false); }
        @Override public List<RuntimeSkillDto> scanSkills(RuntimeType rt) { return List.of(); }
        @Override public String installSkill(RuntimeType rt, String name, java.nio.file.Path dir) { return ".opencode/skills/" + name; }
        @Override public void deleteSkillFiles(RuntimeType rt, String rel, String name) {}
        @Override public java.util.Map<String, Object> readMcpConfig(RuntimeType rt) { return java.util.Map.of(); }
        @Override public void writeMcpConfig(RuntimeType rt, java.util.Map<String, Object> config) {}
        @Override public String getSkillsRootPath(RuntimeType rt) { return "/tmp/test/.opencode/skills"; }
        @Override public void registerWorkflowNodeContext(RuntimeType rt, String agentSessionId, String workItemId,
                                                            String workflowInstanceId, String workflowNodeInstanceId) {}
    };

    @Test
    void refreshSkillsDelegatesScanToGateway() {
        RuntimeResourceService service = new RuntimeResourceService(
                STUB_GATEWAY,
                resolver("/tmp/test-project")
        );

        var response = service.refreshSkills();

        assertThat(response.skillCount()).isZero();
        assertThat(response.skillsPath()).isEqualTo("/tmp/test/.opencode/skills");
    }

    @Test
    void refreshSkillsReturnsGatewayScannedSkills() {
        RuntimeSkillDto skill = new RuntimeSkillDto(
                "fe-requirement-refine",
                "将 FE 需求整理成设计文档",
                ".opencode/skills/fe-requirement-refine",
                "abc123",
                OffsetDateTime.now()
        );
        RuntimeGateway gatewayWithSkill = new RuntimeGateway() {
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
            @Override public RuntimeCapabilities capabilities(RuntimeType rt) { return new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false); }
            @Override public List<RuntimeSkillDto> scanSkills(RuntimeType rt) { return List.of(skill); }
            @Override public String installSkill(RuntimeType rt, String name, java.nio.file.Path dir) { return ".opencode/skills/" + name; }
            @Override public void deleteSkillFiles(RuntimeType rt, String rel, String name) {}
            @Override public java.util.Map<String, Object> readMcpConfig(RuntimeType rt) { return java.util.Map.of(); }
            @Override public void writeMcpConfig(RuntimeType rt, java.util.Map<String, Object> config) {}
            @Override public String getSkillsRootPath(RuntimeType rt) { return "/tmp/test/.opencode/skills"; }
            @Override public void registerWorkflowNodeContext(RuntimeType rt, String agentSessionId, String workItemId,
                                                                String workflowInstanceId, String workflowNodeInstanceId) {}
        };

        RuntimeResourceService service = new RuntimeResourceService(
                gatewayWithSkill,
                resolver("/tmp/test-project")
        );

        var response = service.refreshSkills();

        assertThat(response.skillCount()).isEqualTo(1);
        assertThat(response.skills().get(0).name()).isEqualTo("fe-requirement-refine");
        assertThat(response.skills().get(0).description()).isEqualTo("将 FE 需求整理成设计文档");
        assertThat(response.skills().get(0).relativePath()).isEqualTo(".opencode/skills/fe-requirement-refine");
    }

    @Test
    void refreshSkillsKeepsSnapshotsPerProject(@TempDir Path tempDir) throws Exception {
        Path projectA = Files.createDirectories(tempDir.resolve("project-a"));
        Path projectB = Files.createDirectories(tempDir.resolve("project-b"));
        RuntimeGateway gateway = new RuntimeGateway() {
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
            @Override public RuntimeCapabilities capabilities(RuntimeType rt) { return new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false); }
            @Override public List<RuntimeSkillDto> scanSkills(RuntimeType rt, Path projectWorkdir) {
                return List.of(new RuntimeSkillDto(
                        projectWorkdir.getFileName().toString(),
                        "project skill",
                        ".opencode/skills/" + projectWorkdir.getFileName(),
                        projectWorkdir.getFileName().toString(),
                        OffsetDateTime.now()
                ));
            }
            @Override public List<RuntimeSkillDto> scanSkills(RuntimeType rt) { return List.of(); }
            @Override public String installSkill(RuntimeType rt, String name, Path dir) { return ".opencode/skills/" + name; }
            @Override public void deleteSkillFiles(RuntimeType rt, String rel, String name) {}
            @Override public java.util.Map<String, Object> readMcpConfig(RuntimeType rt) { return java.util.Map.of(); }
            @Override public void writeMcpConfig(RuntimeType rt, java.util.Map<String, Object> config) {}
            @Override public String getSkillsRootPath(RuntimeType rt, Path projectWorkdir) {
                return projectWorkdir.resolve(".opencode/skills").toString();
            }
            @Override public String getSkillsRootPath(RuntimeType rt) { return "/tmp/default/.opencode/skills"; }
            @Override public void registerWorkflowNodeContext(RuntimeType rt, String agentSessionId, String workItemId,
                                                                String workflowInstanceId, String workflowNodeInstanceId) {}
        };
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agentcenter.runtime.projects.Project-A.working-directory", projectA.toString())
                .withProperty("agentcenter.runtime.projects.Project-B.working-directory", projectB.toString());
        RuntimeResourceService service = new RuntimeResourceService(
                gateway,
                new ProjectRuntimeWorkspaceResolver(environment, tempDir.resolve("default").toString())
        );

        service.refreshSkills("Project-A");
        service.refreshSkills("Project-B");

        assertThat(service.currentSkillSnapshot("Project-A").projectRoot()).isEqualTo(projectA.toString());
        assertThat(service.currentSkillSnapshot("Project-A").skills().get(0).name()).isEqualTo("project-a");
        assertThat(service.currentSkillSnapshot("Project-B").projectRoot()).isEqualTo(projectB.toString());
        assertThat(service.currentSkillSnapshot("Project-B").skills().get(0).name()).isEqualTo("project-b");
    }

    private static ProjectRuntimeWorkspaceResolver resolver(String defaultWorkingDirectory) {
        return new ProjectRuntimeWorkspaceResolver(new MockEnvironment(), defaultWorkingDirectory);
    }
}
