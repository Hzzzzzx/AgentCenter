package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeSkillPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void managedProjectSkillsRootUsesOfficialProjectOpenCodeSkillsDirectory() {
        Path root = OpenCodeSkillPaths.managedProjectSkillsRoot(tempDir);

        assertThat(root).isEqualTo(tempDir.resolve(".opencode").resolve("skills").normalize());
    }

    @Test
    void officialGlobalSkillsRootsUsePluralOpenCodeDirectory() {
        var roots = OpenCodeSkillPaths.officialGlobalSkillsRoots(tempDir);

        assertThat(roots)
                .containsExactly(
                        tempDir.resolve(".config").resolve("opencode").resolve("skills").normalize(),
                        tempDir.resolve(".claude").resolve("skills").normalize(),
                        tempDir.resolve(".agents").resolve("skills").normalize());
        assertThat(roots)
                .noneMatch(root -> root.toString().replace('\\', '/').endsWith(".config/opencode/skill"));
    }

    @Test
    void portableRelativePathAlwaysUsesForwardSlashes() {
        Path skillDir = tempDir.resolve(".opencode").resolve("skills").resolve("demo");

        String relativePath = OpenCodeSkillPaths.portableRelativePath(tempDir, skillDir);

        assertThat(relativePath).isEqualTo(".opencode/skills/demo");
    }

    @Test
    void resolvePortableRelativePathAcceptsWindowsSeparators() {
        Path resolved = OpenCodeSkillPaths.resolvePortableRelativePath(tempDir, ".opencode\\skills\\demo");

        assertThat(resolved).isEqualTo(tempDir.resolve(".opencode").resolve("skills").resolve("demo").normalize());
    }
}
