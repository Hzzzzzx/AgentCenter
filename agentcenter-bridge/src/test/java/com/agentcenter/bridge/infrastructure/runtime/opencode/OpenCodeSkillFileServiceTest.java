package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeSkillFileServiceTest {

    @TempDir
    Path tempDir;

    private OpenCodeSkillFileService service;

    @BeforeEach
    void setUp() {
        service = new OpenCodeSkillFileService(tempDir.toString());
    }

    // --- scanSkills ---

    @Test
    void emptySkillsDirectoryReturnsEmptyList() {
        var skills = service.scanSkills();
        assertThat(skills).isEmpty();
    }

    @Test
    void scansSingleSkillWithValidSKILL_md() throws IOException {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: My Skill\ndescription: A test skill\n---\n# My Skill");

        var skills = service.scanSkills();

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).name()).isEqualTo("my-skill");
        assertThat(skills.get(0).description()).isEqualTo("A test skill");
        assertThat(skills.get(0).relativePath()).isEqualTo(".opencode/skills/my-skill");
    }

    @Test
    void scansMultipleSkills() throws IOException {
        Path skillsRoot = tempDir.resolve(".opencode").resolve("skills");

        Path skillA = skillsRoot.resolve("alpha-skill");
        Files.createDirectories(skillA);
        Files.writeString(skillA.resolve("SKILL.md"),
                "---\nname: Alpha\ndescription: First\n---\n");

        Path skillB = skillsRoot.resolve("beta-skill");
        Files.createDirectories(skillB);
        Files.writeString(skillB.resolve("SKILL.md"),
                "---\nname: Beta\ndescription: Second\n---\n");

        var skills = service.scanSkills();

        assertThat(skills).hasSize(2);
        assertThat(skills.get(0).name()).isEqualTo("Alpha");
        assertThat(skills.get(1).name()).isEqualTo("Beta");
    }

    @Test
    void scanChecksumIncludesSkillDirectoryFiles() throws IOException {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: my-skill\ndescription: A test skill\n---\n# My Skill");
        Files.writeString(skillDir.resolve("prompt.md"), "Version 1");

        String initialChecksum = service.scanSkills().get(0).checksum();

        Files.writeString(skillDir.resolve("prompt.md"), "Version 2");

        assertThat(service.scanSkills().get(0).checksum()).isNotEqualTo(initialChecksum);
    }

    @Test
    void ignoresDirectoriesWithoutSKILL_md() throws IOException {
        Path skillsRoot = tempDir.resolve(".opencode").resolve("skills");
        Path emptyDir = skillsRoot.resolve("empty-dir");
        Files.createDirectories(emptyDir);

        var skills = service.scanSkills();

        assertThat(skills).isEmpty();
    }

    // --- installSkill ---

    @Test
    void installsSkillToCorrectDirectory() throws IOException {
        Path sourceDir = tempDir.resolve("source-skill");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("SKILL.md"), "# Installed Skill");
        Files.writeString(sourceDir.resolve("prompt.md"), "Do something");

        String relativePath = service.installSkill("my-skill", sourceDir);

        assertThat(relativePath).isEqualTo(".opencode/skills/my-skill");

        Path installedDir = tempDir.resolve(".opencode").resolve("skills").resolve("my-skill");
        assertThat(installedDir).isDirectory();
        assertThat(installedDir.resolve("SKILL.md")).hasContent("# Installed Skill");
        assertThat(installedDir.resolve("prompt.md")).hasContent("Do something");
    }

    @Test
    void overwritesExistingSkillDirectory() throws IOException {
        Path sourceDir = tempDir.resolve("source-skill");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("SKILL.md"), "Version 2");

        service.installSkill("my-skill", sourceDir);

        Files.writeString(sourceDir.resolve("SKILL.md"), "Version 3");
        service.installSkill("my-skill", sourceDir);

        Path installedFile = tempDir.resolve(".opencode").resolve("skills")
                .resolve("my-skill").resolve("SKILL.md");
        assertThat(installedFile).hasContent("Version 3");
    }

    @Test
    void rejectsPathTraversalInSkillName() {
        Path sourceDir = tempDir.resolve("source-skill");
        assertThatThrownBy(() -> service.installSkill("../escape", sourceDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe characters");
    }

    @Test
    void rejectsBlankSkillName() {
        assertThatThrownBy(() -> service.installSkill("", tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsDotAsSkillName() throws IOException {
        Path skillsRoot = tempDir.resolve(".opencode").resolve("skills");
        Files.createDirectories(skillsRoot);
        Files.writeString(skillsRoot.resolve("existing.txt"), "important");

        assertThatThrownBy(() -> service.installSkill(".", tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank, '.', or '..'");

        assertThat(skillsRoot.resolve("existing.txt")).exists();
    }

    @Test
    void rejectsDotDotAsSkillName() throws IOException {
        Path opencodeDir = tempDir.resolve(".opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("precious.json"), "do-not-delete");

        assertThatThrownBy(() -> service.installSkill("..", tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank, '.', or '..'");

        assertThat(opencodeDir.resolve("precious.json")).exists();
    }

    // --- deleteSkillFiles ---

    @Test
    void deletesByRelativePath() throws IOException {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("to-delete");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "bye");

        service.deleteSkillFiles(".opencode/skills/to-delete", null);

        assertThat(skillDir).doesNotExist();
    }

    @Test
    void deletesByWindowsStylePersistedRelativePath() throws IOException {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("to-delete");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "bye");

        service.deleteSkillFiles(".opencode\\skills\\to-delete", null);

        assertThat(skillDir).doesNotExist();
    }

    @Test
    void refusesToDeleteOutsideSkillsDirectory() {
        assertThatThrownBy(() -> service.deleteSkillFiles("../../dangerous", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to delete");
    }

    // --- getSkillsRootPath ---

    @Test
    void returnsAbsoluteSkillsRootPath() {
        String path = service.getSkillsRootPath();
        assertThat(path.replace('\\', '/')).endsWith(".opencode/skills");
        assertThat(Path.of(path)).isAbsolute();
    }

    // --- readSkillContent ---

    @Test
    void readsManagedSkillContent() throws IOException {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# My Skill\n\nDo the thing.");

        String content = service.readSkillContent("my-skill");

        assertThat(content).contains("# My Skill");
        assertThat(content).contains("Do the thing.");
    }

    @Test
    void readSkillContentReturnsEmptyWhenSkillFileMissing() {
        String content = service.readSkillContent("missing-skill");

        assertThat(content).isEmpty();
    }

    @Test
    void readSkillContentRejectsUnsafeSkillName() {
        assertThatThrownBy(() -> service.readSkillContent("../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe characters");
    }
}
