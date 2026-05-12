package com.agentcenter.bridge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentcenter.bridge.application.ProjectRuntimeWorkspaceResolver;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;

class ArtifactControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void getArtifactReadsMarkdownContentFromRuntimeWorkspaceFileWhenInlineContentMissing() throws Exception {
        ArtifactMapper artifactMapper = mock(ArtifactMapper.class);
        WorkItemMapper workItemMapper = mock(WorkItemMapper.class);
        ProjectRuntimeWorkspaceResolver workspaceResolver = mock(ProjectRuntimeWorkspaceResolver.class);
        ArtifactController controller = new ArtifactController(artifactMapper, workItemMapper, workspaceResolver);

        Path artifactFile = tempDir.resolve("artifacts").resolve("actual-output.md");
        Files.createDirectories(artifactFile.getParent());
        Files.writeString(artifactFile, "# 实际生成的 Markdown\n\n这是文件里的正文。");

        WorkItemEntity workItem = new WorkItemEntity();
        workItem.setId("work-1");
        workItem.setProjectId("project-1");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId("artifact-1");
        artifact.setWorkItemId("work-1");
        artifact.setWorkflowInstanceId("workflow-1");
        artifact.setWorkflowNodeInstanceId("node-1");
        artifact.setArtifactType("MARKDOWN");
        artifact.setTitle("actual-output.md");
        artifact.setContent(null);
        artifact.setStorageUri(artifactFile.toString());
        artifact.setCreatedAt("2026-05-12 10:00:00");

        when(artifactMapper.findById("artifact-1")).thenReturn(artifact);
        when(workItemMapper.findById("work-1")).thenReturn(workItem);
        when(workspaceResolver.resolve("project-1")).thenReturn(tempDir);

        var dto = controller.getArtifact("artifact-1");

        assertThat(dto.content()).contains("# 实际生成的 Markdown");
        assertThat(dto.content()).contains("文件里的正文");
    }

    @Test
    void getArtifactDoesNotReadFilesOutsideRuntimeWorkspace() throws Exception {
        ArtifactMapper artifactMapper = mock(ArtifactMapper.class);
        WorkItemMapper workItemMapper = mock(WorkItemMapper.class);
        ProjectRuntimeWorkspaceResolver workspaceResolver = mock(ProjectRuntimeWorkspaceResolver.class);
        ArtifactController controller = new ArtifactController(artifactMapper, workItemMapper, workspaceResolver);

        Path outside = Files.createTempFile("outside-artifact", ".md");
        Files.writeString(outside, "# 不应暴露");

        WorkItemEntity workItem = new WorkItemEntity();
        workItem.setId("work-1");
        workItem.setProjectId("project-1");

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId("artifact-1");
        artifact.setWorkItemId("work-1");
        artifact.setArtifactType("MARKDOWN");
        artifact.setTitle("outside.md");
        artifact.setContent(null);
        artifact.setStorageUri(outside.toString());
        artifact.setCreatedAt("2026-05-12 10:00:00");

        when(artifactMapper.findById("artifact-1")).thenReturn(artifact);
        when(workItemMapper.findById("work-1")).thenReturn(workItem);
        when(workspaceResolver.resolve("project-1")).thenReturn(tempDir);

        var dto = controller.getArtifact("artifact-1");

        assertThat(dto.content()).isNull();
    }
}
