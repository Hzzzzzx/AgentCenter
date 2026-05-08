package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.translation.AssistantMessageProjector;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventEnvelopeDispatcher;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Subscribes to the opencode serve SSE event stream ({@code GET /event}),
 * extracts raw events, delegates translation to
 * {@link OpenCodeRuntimeEventTranslator}, and dispatches resulting
 * {@link RuntimeEventEnvelope}s via {@link RuntimeEventEnvelopeDispatcher}.
 *
 * <p>A single SSE connection serves all sessions. Events are demultiplexed
 * by extracting the opencode session ID from each event and mapping it
 * back to the agent session ID.</p>
 */
@Component
public class OpenCodeEventSubscriber implements RuntimeTranslationContext {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeEventSubscriber.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final OpenCodeTranslationState translationState;
    private final OpenCodeRuntimeEventTranslator translator;
    private final RuntimeEventEnvelopeDispatcher dispatcher;
    private final AssistantMessageProjector projector;

    private final Map<String, String> opencodeToAgentSession = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "opencode-sse-reader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    public OpenCodeEventSubscriber(ObjectMapper objectMapper,
                                   RuntimeEventEnvelopeDispatcher dispatcher,
                                   AssistantMessageProjector projector) {
        this.objectMapper = objectMapper;
        this.translationState = new OpenCodeTranslationState();
        this.translator = new OpenCodeRuntimeEventTranslator(translationState);
        this.dispatcher = dispatcher;
        this.projector = projector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // --- RuntimeTranslationContext ---

    @Override
    public String getAgentSessionId(String runtimeSessionId) {
        return opencodeToAgentSession.get(runtimeSessionId);
    }

    @Override
    public boolean isUserMessage(String runtimeSessionId, String messageId) {
        return translationState.isUserMessage(runtimeSessionId, messageId);
    }

    @Override
    public void recordUserMessageId(String runtimeSessionId, String messageId) {
        translationState.recordUserMessageId(runtimeSessionId, messageId);
    }

    // --- Session lifecycle ---

    public void registerSession(String opencodeSessionId, String agentSessionId, String baseUrl, String workingDirectory) {
        opencodeToAgentSession.put(opencodeSessionId, agentSessionId);
        translationState.initSession(opencodeSessionId);

        if (subscribed.compareAndSet(false, true)) {
            startSubscription(baseUrl, workingDirectory);
        }
    }

    public void unregisterSession(String opencodeSessionId) {
        String agentSessionId = opencodeToAgentSession.remove(opencodeSessionId);
        translationState.cleanupSession(opencodeSessionId);
        if (agentSessionId != null) {
            projector.cleanupSession(agentSessionId);
        }
    }

    public void shutdown() {
        subscribed.set(false);
        executor.shutdownNow();
    }

    // --- SSE connection management ---

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

    // --- Event translation delegation ---

    private void normalizeAndPublish(JsonNode raw) {
        String eventType = raw.path("type").asText("");
        JsonNode properties = raw.has("properties") ? raw.get("properties") : raw.path("data");

        String opencodeSessionId = extractSessionId(properties);
        if (opencodeSessionId.isEmpty() || eventType.isEmpty()) return;

        String agentSessionId = opencodeToAgentSession.get(opencodeSessionId);
        if (agentSessionId == null) return;

        RuntimeRawEvent rawEvent = new RuntimeRawEvent(RuntimeType.OPENCODE, eventType, raw, opencodeSessionId);
        List<RuntimeEventEnvelope> envelopes = translator.translate(rawEvent, this);
        if (!envelopes.isEmpty()) {
            dispatcher.dispatch(envelopes);
        }
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
