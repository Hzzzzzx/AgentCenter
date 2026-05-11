package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class OpenCodeSkillPaths {

    // AgentCenter manages project-local OpenCode skills. The global roots are
    // kept here to mirror OpenCode discovery rules and avoid singular "skill"
    // path drift in diagnostics or future sync logic.
    private static final String PROJECT_SKILLS_ROOT = ".opencode/skills";
    private static final List<String> OFFICIAL_GLOBAL_SKILL_ROOTS = List.of(
            ".config/opencode/skills",
            ".claude/skills",
            ".agents/skills");

    private OpenCodeSkillPaths() {
    }

    static Path managedProjectSkillsRoot(Path workspace) {
        return resolvePortableRelativePath(workspace, PROJECT_SKILLS_ROOT);
    }

    static String installedSkillRelativePath(String skillName) {
        return PROJECT_SKILLS_ROOT + "/" + skillName;
    }

    static String portableRelativePath(Path base, Path target) {
        return StreamSupport.stream(base.relativize(target).spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }

    static Path resolvePortableRelativePath(Path base, String relativePath) {
        Path resolved = base;
        String normalizedPath = relativePath.replace('\\', '/');
        for (String segment : normalizedPath.split("/")) {
            if (!segment.isBlank()) {
                resolved = resolved.resolve(segment);
            }
        }
        return resolved.normalize();
    }

    static List<Path> officialGlobalSkillsRoots(Path home) {
        return OFFICIAL_GLOBAL_SKILL_ROOTS.stream()
                .map(root -> resolvePortableRelativePath(home, root))
                .toList();
    }
}
