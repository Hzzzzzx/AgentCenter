package com.agentcenter.bridge.application.artifact;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.agentcenter.bridge.domain.artifact.ArtifactType;

public final class ArtifactBlockParser {

    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "<!--\\s*AGENTCENTER_ARTIFACT_BEGIN\\s*(.*?)-->\\s*([\\s\\S]*?)\\s*<!--\\s*AGENTCENTER_ARTIFACT_END\\s*-->",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");

    private ArtifactBlockParser() {}

    public static List<ArtifactBlock> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ArtifactBlock> blocks = new ArrayList<>();
        Matcher matcher = BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            String header = matcher.group(1);
            String content = matcher.group(2) != null ? matcher.group(2).trim() : "";
            if (content.isBlank()) {
                continue;
            }
            String title = firstNonBlank(headerValue(header, "title"), inferTitle(content), "对话产物.md");
            ArtifactType type = parseType(firstNonBlank(headerValue(header, "type"), "MARKDOWN"));
            String filePath = headerValue(header, "file_path");
            blocks.add(new ArtifactBlock(title, type, content, filePath));
        }
        return blocks;
    }

    private static String headerValue(String header, String key) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = key + ":";
        for (String line : header.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String value = trimmed.substring(prefix.length()).trim();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static String inferTitle(String content) {
        Matcher matcher = HEADING_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String heading = matcher.group(1).trim();
        if (heading.isBlank()) {
            return null;
        }
        return heading.endsWith(".md") ? heading : heading + ".md";
    }

    private static ArtifactType parseType(String value) {
        try {
            return ArtifactType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return ArtifactType.MARKDOWN;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record ArtifactBlock(
            String title,
            ArtifactType artifactType,
            String content,
            String filePath
    ) {}
}
