package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventStreamTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeEndpoint;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeTextEncoding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SSE-based {@link RuntimeEventStreamTransport} for OpenCode runtime.
 * Connects to {@code GET /event} on the opencode serve instance,
 * parses SSE data blocks into {@link RuntimeRawEvent}s, and delivers
 * them to the provided {@link RuntimeEventSink}.
 */
public class OpenCodeSseEventStreamTransport implements RuntimeEventStreamTransport {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeSseEventStreamTransport.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    private String baseUrl;
    private String workingDirectory;

    public OpenCodeSseEventStreamTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "opencode-sse-reader");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set connection parameters before calling {@link #subscribe}.
     * These values are only known at runtime after the opencode process is running.
     */
    public void configure(String baseUrl, String workingDirectory) {
        this.baseUrl = baseUrl;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public SubscriptionHandle subscribe(RuntimeEventSink sink) {
        AtomicBoolean running = new AtomicBoolean(true);

        SseSubscriptionHandle handle = new SseSubscriptionHandle(h -> {
            running.set(false);
        });

        executor.submit(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    connectAndRead(sink, running);
                } catch (Exception e) {
                    if (!running.get()) break;
                    sink.onError(new RuntimeTransportException(
                        "SSE connection lost: " + e.getMessage(), e, true));
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (running.compareAndSet(true, false)) {
                sink.onClose();
            }
        });

        return handle;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void connectAndRead(RuntimeEventSink sink, AtomicBoolean running) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(OpenCodeEndpoint.uri(baseUrl, "/event"))
                .header("x-opencode-directory", workingDirectory)
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("opencode SSE returned HTTP " + response.statusCode());
        }

        log.info("Connected to opencode serve SSE at {}/event", baseUrl);

        StringBuilder eventBuffer = new StringBuilder();
        try (var lines = new BufferedReader(new InputStreamReader(response.body(), OpenCodeTextEncoding.WIRE_CHARSET)).lines()) {
            lines.forEach(line -> {
                if (!running.get()) return;

                if (line.isBlank()) {
                    String block = eventBuffer.toString().trim();
                    if (!block.isEmpty()) {
                        parseAndDeliver(block, sink);
                    }
                    eventBuffer.setLength(0);
                } else {
                    eventBuffer.append(line).append("\n");
                }
            });
        }
    }

    private void parseAndDeliver(String block, RuntimeEventSink sink) {
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

        String eventType = raw.path("type").asText("");
        JsonNode properties = raw.has("properties") ? raw.get("properties") : raw.path("data");
        String sessionId = extractSessionId(properties);

        if (sessionId.isEmpty() && eventType.isEmpty()) return;

        RuntimeRawEvent rawEvent = new RuntimeRawEvent(
            RuntimeType.OPENCODE, eventType, raw, sessionId);

        sink.onEvent(rawEvent);
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
