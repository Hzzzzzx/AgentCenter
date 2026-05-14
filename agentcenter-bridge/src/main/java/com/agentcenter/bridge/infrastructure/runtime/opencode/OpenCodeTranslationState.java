package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OpenCodeTranslationState {
    private final Map<String, Map<String, String>> textPartSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PartMetadata>> partMetadata = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runningTools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userMessageIds = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> questionRequests = new ConcurrentHashMap<>();

    public void initSession(String opencodeSessionId) {
        textPartSnapshots.computeIfAbsent(opencodeSessionId, k -> new ConcurrentHashMap<>());
        partMetadata.computeIfAbsent(opencodeSessionId, k -> new ConcurrentHashMap<>());
        runningTools.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
        userMessageIds.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
        questionRequests.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
    }

    public void cleanupSession(String opencodeSessionId) {
        textPartSnapshots.remove(opencodeSessionId);
        partMetadata.remove(opencodeSessionId);
        runningTools.remove(opencodeSessionId);
        userMessageIds.remove(opencodeSessionId);
        questionRequests.remove(opencodeSessionId);
    }

    public void recordPartMetadata(String opencodeSessionId, String partId, String partType, String messageId) {
        if (partId == null || partId.isBlank()) {
            return;
        }
        partMetadata
                .computeIfAbsent(opencodeSessionId, k -> new ConcurrentHashMap<>())
                .put(partId, new PartMetadata(partType == null ? "" : partType, messageId == null ? "" : messageId));
    }

    public PartMetadata findPartMetadata(String opencodeSessionId, String partId) {
        Map<String, PartMetadata> byPart = partMetadata.get(opencodeSessionId);
        return byPart == null ? null : byPart.get(partId);
    }

    public String recordTextDelta(String opencodeSessionId, String partId, String delta) {
        if (partId == null || partId.isBlank()) {
            return delta;
        }
        textPartSnapshots
                .computeIfAbsent(opencodeSessionId, k -> new ConcurrentHashMap<>())
                .merge(partId, delta, String::concat);
        return delta;
    }

    public String recordTextSnapshot(String opencodeSessionId, String partId, String text) {
        if (partId == null || partId.isBlank()) {
            return text;
        }
        Map<String, String> snapshots = textPartSnapshots
                .computeIfAbsent(opencodeSessionId, k -> new ConcurrentHashMap<>());
        String previous = snapshots.put(partId, text);
        if (previous == null || previous.isEmpty()) {
            return text;
        }
        if (text.equals(previous)) {
            return "";
        }
        if (text.startsWith(previous)) {
            return text.substring(previous.length());
        }
        return text;
    }

    public boolean isUserMessage(String opencodeSessionId, String messageId) {
        Set<String> ids = userMessageIds.get(opencodeSessionId);
        return ids != null && ids.contains(messageId);
    }

    public void recordUserMessageId(String opencodeSessionId, String messageId) {
        userMessageIds.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet()).add(messageId);
    }

    public boolean addRunningTool(String opencodeSessionId, String callId) {
        Set<String> tools = runningTools.get(opencodeSessionId);
        return tools != null && tools.add(callId);
    }

    public void removeRunningTool(String opencodeSessionId, String callId) {
        Set<String> tools = runningTools.get(opencodeSessionId);
        if (tools != null) tools.remove(callId);
    }

    public boolean addQuestionRequest(String opencodeSessionId, String... keys) {
        Set<String> requests = questionRequests.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
        boolean hasNewKey = false;
        for (String key : keys) {
            if (key != null && !key.isBlank() && !requests.contains(key)) {
                hasNewKey = true;
            }
        }
        if (!hasNewKey) {
            return false;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                requests.add(key);
            }
        }
        return true;
    }

    public record PartMetadata(String partType, String messageId) {}
}
