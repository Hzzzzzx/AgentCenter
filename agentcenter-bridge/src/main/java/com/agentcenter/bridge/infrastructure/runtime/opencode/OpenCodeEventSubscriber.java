package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Subscribes to the opencode serve SSE event stream ({@code GET /event}),
 * parses events, filters by session, translates them to {@link RuntimeEventDto},
 * and publishes via {@link RuntimeEventService}.
 *
 * <p>A single SSE connection serves all sessions. Events are demultiplexed
 * by extracting the opencode session ID from each event and mapping it
 * back to the agent session ID.</p>
 */
@Component
public class OpenCodeEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeEventSubscriber.class);

    private final RuntimeEventService eventService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final Map<String, String> opencodeToAgentSession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenTextParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenReasoningParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runningTools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userMessageIds = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "opencode-sse-reader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    public OpenCodeEventSubscriber(RuntimeEventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Registers the mapping between opencode session ID and agent session ID.
     * Triggers SSE subscription if not already active.
     */
    public void registerSession(String opencodeSessionId, String agentSessionId, String baseUrl, String workingDirectory) {
        opencodeToAgentSession.put(opencodeSessionId, agentSessionId);
        seenTextParts.computeIfAbsent(opencodeSessionId, k -> new HashSet<>());
        seenReasoningParts.computeIfAbsent(opencodeSessionId, k -> new HashSet<>());
        runningTools.computeIfAbsent(opencodeSessionId, k -> new HashSet<>());
        userMessageIds.computeIfAbsent(opencodeSessionId, k -> new HashSet<>());

        if (subscribed.compareAndSet(false, true)) {
            startSubscription(baseUrl, workingDirectory);
        }
    }

    /**
     * Unregisters a session and cleans up tracking state.
     */
    public void unregisterSession(String opencodeSessionId) {
        opencodeToAgentSession.remove(opencodeSessionId);
        seenTextParts.remove(opencodeSessionId);
        seenReasoningParts.remove(opencodeSessionId);
        runningTools.remove(opencodeSessionId);
        userMessageIds.remove(opencodeSessionId);
    }

    /**
     * Records a user message ID so that we can filter out user text parts from the event stream.
     */
    public void recordUserMessageId(String opencodeSessionId, String messageId) {
        Set<String> ids = userMessageIds.get(opencodeSessionId);
        if (ids != null) {
            ids.add(messageId);
        }
    }

    public void shutdown() {
        subscribed.set(false);
        executor.shutdownNow();
    }

    private void startSubscription(String baseUrl, String workingDirectory) {
        executor.submit(() -> {
            while (subscribed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    connectAndRead(baseUrl, workingDirectory);
                } catch (Exception e) {
                    if (!subscribed.get()) break;
                    log.error("SSE connection to opencode serve lost, reconnecting in 3s: {}", e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void connectAndRead(String baseUrl, String workingDirectory) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/event"))
                .header("x-opencode-directory", workingDirectory)
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();

        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            throw new RuntimeException("opencode SSE returned HTTP " + response.statusCode());
        }

        log.info("Connected to opencode serve SSE at {}/event", baseUrl);

        StringBuilder eventBuffer = new StringBuilder();
        try (var lines = response.body()) {
            lines.forEach(line -> {
                if (!subscribed.get()) return;

                if (line.isBlank()) {
                    String block = eventBuffer.toString().trim();
                    if (!block.isEmpty()) {
                        handleSseBlock(block);
                    }
                    eventBuffer.setLength(0);
                } else {
                    eventBuffer.append(line).append("\n");
                }
            });
        }
    }

    private void handleSseBlock(String block) {
        StringBuilder dataBuilder = new StringBuilder();
        for (String line : block.split("\n")) {
            if (line.startsWith("data:")) {
                dataBuilder.append(line.substring(5).trim()).append("\n");
            }
        }
        String data = dataBuilder.toString().trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return;

        JsonNode raw;
        try {
            raw = objectMapper.readTree(data);
        } catch (Exception e) {
            log.debug("Ignoring non-JSON SSE event: {}", data.substring(0, Math.min(data.length(), 120)));
            return;
        }

        normalizeAndPublish(raw);
    }

    private void normalizeAndPublish(JsonNode raw) {
        String eventType = raw.path("type").asText("");
        JsonNode properties = raw.has("properties") ? raw.get("properties") : raw.path("data");

        String opencodeSessionId = extractSessionId(properties);
        if (opencodeSessionId.isEmpty() || eventType.isEmpty()) return;

        String agentSessionId = opencodeToAgentSession.get(opencodeSessionId);
        if (agentSessionId == null) return;

        switch (eventType) {
            case "message.updated" -> handleMessageUpdated(opencodeSessionId, properties);
            case "message.part.updated", "message.part.delta" -> handleMessagePart(opencodeSessionId, agentSessionId, properties, eventType);
            case "session.status" -> handleSessionStatus(agentSessionId, properties);
            case "session.idle" -> handleSessionIdle(agentSessionId);
            case "permission.asked", "permission.updated" -> handlePermission(agentSessionId, properties);
            case "session.error" -> handleSessionError(agentSessionId, properties);
            default -> {}
        }
    }

    private void handleMessageUpdated(String opencodeSessionId, JsonNode properties) {
        JsonNode info = properties.path("info");
        if ("user".equals(info.path("role").asText("")) && info.has("id")) {
            recordUserMessageId(opencodeSessionId, info.path("id").asText());
        }
    }

    private void handleMessagePart(String opencodeSessionId, String agentSessionId, JsonNode properties, String eventType) {
        JsonNode part = properties.path("part");
        if (part.isMissingNode()) part = properties;

        // Filter out user message text parts
        String msgId = part.path("messageID").asText(part.path("message_id").asText(""));
        Set<String> userMsgIds = userMessageIds.get(opencodeSessionId);
        if (!msgId.isEmpty() && userMsgIds != null && userMsgIds.contains(msgId)) return;

        String delta = properties.path("delta").asText("");
        String partType = part.path("type").asText("");

        if ("text".equals(partType)) {
            String text = resolvePartText(opencodeSessionId, seenTextParts, part, delta);
            if (!text.isEmpty()) {
                publishDelta(agentSessionId, text);
            }
        } else if ("tool".equals(partType)) {
            handleToolPart(opencodeSessionId, agentSessionId, part);
        }
    }

    private String resolvePartText(String opencodeSessionId, Map<String, Set<String>> seenMap, JsonNode part, String delta) {
        if (!delta.isEmpty()) return delta;

        String partText = part.path("text").asText("");
        if (partText.isEmpty()) return "";

        String partId = part.path("id").asText("");
        if (partId.isEmpty()) return partText;

        Set<String> seen = seenMap.get(opencodeSessionId);
        if (seen == null) return partText;
        if (seen.contains(partId)) return "";
        seen.add(partId);
        return partText;
    }

    private void handleToolPart(String opencodeSessionId, String agentSessionId, JsonNode part) {
        String callId = part.path("callID").asText(part.path("call_id").asText(part.path("id").asText("tool_" + System.currentTimeMillis())));
        String skillName = part.path("tool").asText(part.path("name").asText("unknown"));
        JsonNode state = part.path("state");
        String status = state.path("status").asText("running");

        Set<String> tools = runningTools.get(opencodeSessionId);
        if (tools == null) return;

        if (("running".equals(status) || "completed".equals(status) || "error".equals(status))
                && tools.add(callId)) {
            publishEvent(agentSessionId, RuntimeEventType.SKILL_STARTED,
                    payload("skill_started", skillName, Map.of("toolCallId", callId)));
        }

        if ("completed".equals(status) || "error".equals(status)) {
            String output = "error".equals(status)
                    ? stringifyValue(state.path("error").isMissingNode() ? part.path("error") : state.path("error"))
                    : stringifyValue(state.path("output").isMissingNode()
                    ? (state.path("result").isMissingNode() ? part.path("output") : state.path("result"))
                    : state.path("output"));
            boolean isError = "error".equals(status);
            publishEvent(agentSessionId, RuntimeEventType.SKILL_COMPLETED,
                    payload("skill_completed", skillName, Map.of(
                            "toolCallId", callId, "isError", isError, "output", output)));
            tools.remove(callId);
        }
    }

    private void handleSessionStatus(String agentSessionId, JsonNode properties) {
        JsonNode statusNode = properties.path("status");
        String rawStatus = statusNode.isObject() ? statusNode.path("type").asText("") : statusNode.asText("");
        if (rawStatus.isEmpty()) rawStatus = properties.path("type").asText("unknown");
        String status = "busy".equals(rawStatus) ? "running" : rawStatus;

        publishEvent(agentSessionId, RuntimeEventType.STATUS,
                    payload("status", status, Map.of()));
    }

    private void handleSessionIdle(String agentSessionId) {
        publishEvent(agentSessionId, RuntimeEventType.STATUS,
                payload("status", "waiting_user", Map.of()));
    }

    private void handlePermission(String agentSessionId, JsonNode properties) {
        String permissionId = properties.path("id").asText("");
        String permission = properties.path("permission").asText(properties.path("type").asText("opencode_permission"));
        String skillName = properties.path("tool").path("tool").asText(
                properties.path("tool").path("name").asText(permission));

        publishEvent(agentSessionId, RuntimeEventType.PERMISSION_REQUIRED,
                payload("permission_required", skillName, Map.of(
                        "permissionId", permissionId,
                        "title", properties.path("title").asText("OpenCode permission: " + permission))));
    }

    private void handleSessionError(String agentSessionId, JsonNode properties) {
        JsonNode error = properties.path("error");
        String reason = error.path("data").path("message").asText("");
        if (reason.isEmpty()) reason = error.path("name").asText("");
        if (reason.isEmpty()) reason = properties.path("message").asText("unknown OpenCode session error");

        publishEvent(agentSessionId, RuntimeEventType.ERROR,
                payload("status", "failed", Map.of("reason", reason)));
    }

    private void publishDelta(String agentSessionId, String text) {
        publishEvent(agentSessionId, RuntimeEventType.ASSISTANT_DELTA,
                payload("assistant_delta", text, Map.of()));
    }

    private void publishEvent(String agentSessionId, RuntimeEventType type, String payloadJson) {
        RuntimeEventDto event = new RuntimeEventDto(
                null, agentSessionId, null, null, null,
                type, RuntimeEventSource.OPENCODE, payloadJson, null);
        eventService.publishEvent(event);
    }

    private String payload(String type, Object label, Map<String, Object> extra) {
        try {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("type", type);
            map.put("label", label);
            map.putAll(extra);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"type\":\"" + type + "\"}";
        }
    }

    private String stringifyValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String extractSessionId(JsonNode value) {
        if (value == null) return "";
        String sid = value.path("sessionID").asText("");
        if (!sid.isEmpty()) return sid;
        sid = value.path("session_id").asText("");
        if (!sid.isEmpty()) return sid;
        JsonNode info = value.path("info");
        sid = info.path("sessionID").asText("");
        if (!sid.isEmpty()) return sid;
        sid = info.path("session_id").asText("");
        if (!sid.isEmpty()) return sid;
        sid = info.path("id").asText("");
        if (!sid.isEmpty()) return sid;
        JsonNode part = value.path("part");
        sid = part.path("sessionID").asText("");
        if (!sid.isEmpty()) return sid;
        return part.path("session_id").asText("");
    }
}
