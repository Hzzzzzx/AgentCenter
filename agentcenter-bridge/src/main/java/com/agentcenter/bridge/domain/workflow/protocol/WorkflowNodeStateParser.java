package com.agentcenter.bridge.domain.workflow.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses AGENTCENTER_NODE_STATE comment blocks from Agent output text.
 * Tolerant of formatting variations. Never throws.
 */
public final class WorkflowNodeStateParser {

    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "<!--\\s*AGENTCENTER_NODE_STATE\\s*(.*?)-->",
            Pattern.DOTALL
    );

    private static final Pattern KV_PATTERN = Pattern.compile(
            "\\s*(\\w+):\\s*(.*)"
    );

    private static final Pattern ITEM_HEADER = Pattern.compile(
            "\\s*-\\s+id:\\s*(.+)"
    );

    private WorkflowNodeStateParser() {}

    public static String stripStateBlock(String text) {
        if (text == null || text.isBlank()) return text;
        return BLOCK_PATTERN.matcher(text).replaceAll("").trim();
    }

    public static WorkflowNodeState parse(String text) {
        if (text == null || text.isBlank()) {
            return WorkflowNodeState.defaultInProgress();
        }

        try {
            Matcher blockMatcher = BLOCK_PATTERN.matcher(text);
            if (!blockMatcher.find()) {
                return WorkflowNodeState.defaultInProgress();
            }

            String rawBlock = blockMatcher.group(1).trim();
            return parseBlockContent(rawBlock);
        } catch (Exception e) {
            return WorkflowNodeState.defaultInProgress();
        }
    }

    private static WorkflowNodeState parseBlockContent(String content) {
        WorkflowNodeStateStatus status = WorkflowNodeStateStatus.IN_PROGRESS;
        String reason = "";
        String artifactTitle = null;

        String[] lines = content.split("\\n");
        int interactionsLineIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("interactions:")) {
                interactionsLineIdx = i;
                break;
            }
        }

        int endIdx = interactionsLineIdx >= 0 ? interactionsLineIdx : lines.length;
        for (int i = 0; i < endIdx; i++) {
            Matcher m = KV_PATTERN.matcher(lines[i]);
            if (m.matches()) {
                String key = m.group(1).trim();
                String value = m.group(2).trim();
                switch (key) {
                    case "status" -> status = parseStatus(value);
                    case "reason" -> reason = value;
                    case "artifact_title" -> artifactTitle = value;
                    default -> {}
                }
            }
        }

        List<WorkflowNodeInteraction> interactions = new ArrayList<>();
        if (interactionsLineIdx >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = interactionsLineIdx + 1; i < lines.length; i++) {
                sb.append(lines[i]).append('\n');
            }
            interactions = parseInteractions(sb.toString());
        }

        return new WorkflowNodeState(status, reason, artifactTitle, interactions, content);
    }

    private static List<WorkflowNodeInteraction> parseInteractions(String section) {
        List<ItemSpan> spans = findListItems(section, 2);
        List<WorkflowNodeInteraction> result = new ArrayList<>();
        for (ItemSpan span : spans) {
            WorkflowNodeInteraction interaction = parseSingleInteraction(span.text());
            if (interaction != null) result.add(interaction);
        }
        return result;
    }

    private static WorkflowNodeInteraction parseSingleInteraction(String itemText) {
        WorkflowNodeInteraction interaction = new WorkflowNodeInteraction();
        String[] lines = itemText.split("\\n");

        int propertyIndent = -1;
        String currentSubKey = null;
        StringBuilder subBuffer = new StringBuilder();

        for (String line : lines) {
            int indent = countIndent(line);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (propertyIndent < 0) {
                Matcher headerMatch = ITEM_HEADER.matcher(line);
                if (headerMatch.matches()) {
                    interaction.setId(headerMatch.group(1).trim());
                }
                propertyIndent = indent + 2;
                continue;
            }

            if (indent == propertyIndent) {
                Matcher kvMatch = KV_PATTERN.matcher(trimmed);
                if (kvMatch.matches()) {
                    String key = kvMatch.group(1);

                    if (currentSubKey != null) {
                        applySubSection(interaction, currentSubKey, subBuffer.toString());
                        currentSubKey = null;
                        subBuffer = new StringBuilder();
                    }

                    if ("options".equals(key) || "fields".equals(key)) {
                        currentSubKey = key;
                        subBuffer = new StringBuilder();
                        continue;
                    }

                    String value = kvMatch.group(2).trim();
                    switch (key) {
                        case "type" -> interaction.setType(parseInteractionType(value));
                        case "title" -> interaction.setTitle(value);
                        case "question" -> interaction.setQuestion(value);
                        case "selection" -> interaction.setSelection(value);
                        case "allow_custom" -> interaction.setAllowCustom(parseBoolean(value));
                        case "required" -> interaction.setRequired(parseBoolean(value));
                        default -> {}
                    }
                }
            } else if (indent > propertyIndent && currentSubKey != null) {
                subBuffer.append(line).append('\n');
            }
        }

        if (currentSubKey != null) {
            applySubSection(interaction, currentSubKey, subBuffer.toString());
        }

        return interaction;
    }

    private static void applySubSection(WorkflowNodeInteraction interaction,
                                         String key, String content) {
        if ("options".equals(key)) {
            interaction.setOptions(parseSubItems(content, WorkflowNodeStateParser::parseOptionItem));
        } else if ("fields".equals(key)) {
            interaction.setFields(parseSubItems(content, WorkflowNodeStateParser::parseFieldItem));
        }
    }

    private static <T> List<T> parseSubItems(String section, ItemParser<T> parser) {
        List<ItemSpan> spans = findListItems(section, 6);
        List<T> result = new ArrayList<>();
        for (ItemSpan span : spans) {
            T item = parser.parse(span.text());
            if (item != null) result.add(item);
        }
        return result;
    }

    private static WorkflowNodeInteraction.InteractionOption parseOptionItem(String text) {
        String id = null, label = null, description = null;
        String[] lines = text.split("\\n");
        Matcher m = ITEM_HEADER.matcher(lines[0]);
        if (m.matches()) id = m.group(1).trim();

        for (int i = 1; i < lines.length; i++) {
            Matcher kv = KV_PATTERN.matcher(lines[i].trim());
            if (kv.matches()) {
                switch (kv.group(1)) {
                    case "label" -> label = kv.group(2).trim();
                    case "description" -> description = kv.group(2).trim();
                    default -> {}
                }
            }
        }
        return id != null ? new WorkflowNodeInteraction.InteractionOption(id, label, description) : null;
    }

    private static WorkflowNodeInteraction.InteractionField parseFieldItem(String text) {
        String id = null, label = null, type = "text";
        boolean required = true;
        String[] lines = text.split("\\n");
        Matcher m = ITEM_HEADER.matcher(lines[0]);
        if (m.matches()) id = m.group(1).trim();

        for (int i = 1; i < lines.length; i++) {
            Matcher kv = KV_PATTERN.matcher(lines[i].trim());
            if (kv.matches()) {
                switch (kv.group(1)) {
                    case "label" -> label = kv.group(2).trim();
                    case "type" -> type = kv.group(2).trim();
                    case "required" -> required = parseBoolean(kv.group(2).trim());
                    default -> {}
                }
            }
        }
        return id != null ? new WorkflowNodeInteraction.InteractionField(id, label, type, required) : null;
    }

    private static List<ItemSpan> findListItems(String section, int targetIndent) {
        String[] lines = section.split("\\n");
        List<Integer> positions = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int indent = countIndent(lines[i]);
            if (Math.abs(indent - targetIndent) <= 1 && lines[i].trim().startsWith("- id:")) {
                positions.add(i);
            }
        }

        List<ItemSpan> spans = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int startLine = positions.get(i);
            int endLine = (i + 1 < positions.size()) ? positions.get(i + 1) : lines.length;

            StringBuilder sb = new StringBuilder();
            for (int j = startLine; j < endLine; j++) {
                sb.append(lines[j]).append('\n');
            }
            spans.add(new ItemSpan(sb.toString()));
        }

        return spans;
    }

    private static int countIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 2;
            else break;
        }
        return count;
    }

    private static WorkflowNodeStateStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return WorkflowNodeStateStatus.IN_PROGRESS;
        try {
            return WorkflowNodeStateStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return WorkflowNodeStateStatus.IN_PROGRESS;
        }
    }

    private static WorkflowNodeInteractionType parseInteractionType(String value) {
        if (value == null || value.isBlank()) return WorkflowNodeInteractionType.ASK_USER;
        try {
            return WorkflowNodeInteractionType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return WorkflowNodeInteractionType.ASK_USER;
        }
    }

    private static boolean parseBoolean(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    @FunctionalInterface
    private interface ItemParser<T> {
        T parse(String text);
    }

    private record ItemSpan(String text) {}
}
