package com.agentcenter.bridge.application.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.domain.artifact.ArtifactType;

class ArtifactBlockParserTest {

    @Test
    void parsesExplicitArtifactBlock() {
        var blocks = ArtifactBlockParser.parse("""
                这是普通说明。

                <!-- AGENTCENTER_ARTIFACT_BEGIN
                title: FE2001 登录重构 PRD.md
                type: MARKDOWN
                -->
                # FE2001 登录重构 PRD

                ## 验收标准
                - 可以被保存。
                <!-- AGENTCENTER_ARTIFACT_END -->
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).title()).isEqualTo("FE2001 登录重构 PRD.md");
        assertThat(blocks.get(0).artifactType()).isEqualTo(ArtifactType.MARKDOWN);
        assertThat(blocks.get(0).content()).contains("# FE2001 登录重构 PRD");
    }

    @Test
    void ignoresRegularMarkdownWithoutProtocol() {
        var blocks = ArtifactBlockParser.parse("# 普通回答\n\n这只是解释。");

        assertThat(blocks).isEmpty();
    }

    @Test
    void infersTitleFromHeadingWhenHeaderOmitsTitle() {
        var blocks = ArtifactBlockParser.parse("""
                <!-- AGENTCENTER_ARTIFACT_BEGIN
                type: REPORT
                -->
                # 周报总结

                内容。
                <!-- AGENTCENTER_ARTIFACT_END -->
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).title()).isEqualTo("周报总结.md");
        assertThat(blocks.get(0).artifactType()).isEqualTo(ArtifactType.REPORT);
    }
}
