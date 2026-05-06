package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;

/**
 * Connects to {@code opencode serve} via HTTP API to create sessions,
 * send messages via {@code prompt_async}, and consume SSE events.
 *
 * <p>Session ID mapping: agentSessionId → opencodeSessionId (in memory).</p>
 *
 * <p>Event flow: opencode SSE → {@link OpenCodeEventSubscriber} →
 * {@link com.agentcenter.bridge.application.RuntimeEventService} →
 * Java SSE to frontend.</p>
 */
@Component("openCodeRuntimeAdapter")
@Primary
@ConditionalOnProperty(name = "agentcenter.runtime.opencode.serve.enabled", havingValue = "true")
public class OpenCodeRuntimeAdapter implements AgentRuntimeAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeRuntimeAdapter.class);

    private final OpenCodeProcessManager processManager;
    private final OpenCodeEventSubscriber eventSubscriber;
    private final ObjectMapper objectMapper;
    private final String agent;

    private final Map<String, String> agentToOpencodeSession = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenCodeRuntimeAdapter(
            OpenCodeProcessManager processManager,
            OpenCodeEventSubscriber eventSubscriber,
            ObjectMapper objectMapper,
            @Value("${agentcenter.runtime.opencode.serve.agent:build}") String agent) {
        this.processManager = processManager;
        this.eventSubscriber = eventSubscriber;
        this.objectMapper = objectMapper;
        this.agent = agent;
    }

    @Override
    public String createSession(String workItemId, String agentSessionId) {
        if (!processManager.isEnabled()) {
            throw new IllegalStateException("OpenCode serve adapter is disabled");
        }

        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();

        String title = "AgentCenter Session";
        if (workItemId != null && !workItemId.isBlank()) {
            title = "AgentCenter · " + workItemId;
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        ArrayNode permissions = body.putArray("permission");
        ObjectNode perm = permissions.addObject();
        perm.put("permission", "edit");
        perm.put("pattern", "*");
        perm.put("action", "ask");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session"))
                .header("Content-Type", "application/json")
                .header("x-opencode-directory", cwd.toString())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(writeValue(body)))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create opencode session: HTTP " + response.statusCode() + " " + response.body());
        }

        JsonNode result = parseJson(response.body());
        String opencodeSessionId = result.path("id").asText("");
        if (opencodeSessionId.isEmpty()) {
            throw new RuntimeException("opencode session response missing id: " + response.body());
        }

        String agentSid = (agentSessionId != null && !agentSessionId.isBlank())
                ? agentSessionId
                : (workItemId != null ? "acs_" + workItemId : "acs_" + System.currentTimeMillis());
        agentToOpencodeSession.put(agentSid, opencodeSessionId);

        eventSubscriber.registerSession(opencodeSessionId, agentSid, baseUrl, cwd.toString());

        log.info("Created opencode session {} → agent session {}", opencodeSessionId, agentSid);
        return agentSid;
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        sendMessage(sessionId, "Execute skill: " + skillName + "\n\n" + inputContext);
        return new SkillRunResult(true, "Skill dispatched to opencode", "TEXT", null, false);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        String opencodeSessionId = agentToOpencodeSession.get(sessionId);
        if (opencodeSessionId == null) {
            throw new IllegalArgumentException("No opencode session mapped for agent session: " + sessionId);
        }

        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent", agent);
        ArrayNode parts = body.putArray("parts");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + opencodeSessionId + "/prompt_async"))
                .header("Content-Type", "application/json")
                .header("x-opencode-directory", cwd.toString())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(writeValue(body)))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 300) {
            throw new RuntimeException("opencode prompt_async failed: HTTP " + response.statusCode() + " " + response.body());
        }

        log.debug("Sent message to opencode session {} (agent session {})", opencodeSessionId, sessionId);
    }

    @Override
    public void cancel(String sessionId) {
        String opencodeSessionId = agentToOpencodeSession.remove(sessionId);
        if (opencodeSessionId != null) {
            eventSubscriber.unregisterSession(opencodeSessionId);
            log.info("Cancelled opencode session {} (agent session {})", opencodeSessionId, sessionId);
        }
    }

    @PreDestroy
    public void destroy() {
        eventSubscriber.shutdown();
        processManager.shutdown();
    }

    public String getOpencodeSessionId(String agentSessionId) {
        return agentToOpencodeSession.get(agentSessionId);
    }

    private HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request to opencode serve failed: " + e.getMessage(), e);
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }
}
