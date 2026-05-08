package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OpenCodeTranslationState {
    private final Map<String, Set<String>> seenTextParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runningTools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userMessageIds = new ConcurrentHashMap<>();

    public void initSession(String opencodeSessionId) {
        seenTextParts.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
        runningTools.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
        userMessageIds.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet());
    }

    public void cleanupSession(String opencodeSessionId) {
        seenTextParts.remove(opencodeSessionId);
        runningTools.remove(opencodeSessionId);
        userMessageIds.remove(opencodeSessionId);
    }

    public boolean isSeenTextPart(String opencodeSessionId, String partId) {
        Set<String> seen = seenTextParts.get(opencodeSessionId);
        return seen != null && seen.contains(partId);
    }

    public void markSeenTextPart(String opencodeSessionId, String partId) {
        seenTextParts.computeIfAbsent(opencodeSessionId, k -> ConcurrentHashMap.newKeySet()).add(partId);
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
}
