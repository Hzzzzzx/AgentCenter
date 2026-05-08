package com.agentcenter.bridge.infrastructure.runtime.websocket;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEnvelopeKind;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventStreamTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified WebSocket transport implementing both {@link RuntimeCommandTransport}
 * and {@link RuntimeEventStreamTransport} over a single bidirectional connection.
 *
 * <p>Incoming frames are routed by {@link RuntimeEnvelopeKind}:
 * <ul>
 *   <li>ACK/NACK → complete pending command futures</li>
 *   <li>EVENT → deliver to all registered sinks</li>
 * </ul>
 */
public class WebSocketRuntimeTransport implements RuntimeCommandTransport, RuntimeEventStreamTransport {

    private final ObjectMapper objectMapper;
    private final WebSocketClientWrapper wsClient;
    private final ConcurrentMap<String, CompletableFuture<RuntimeAckEnvelope>> pendingAcks =
        new ConcurrentHashMap<>();
    private final List<SinkRegistration> activeSinks = new CopyOnWriteArrayList<>();
    private final AtomicLong sinkIdCounter = new AtomicLong(0);

    // --- Public constructor (production) ---
    public WebSocketRuntimeTransport(URI serverUri, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.wsClient = new WebSocketClientWrapper(
            serverUri,
            this::handleIncoming,
            this::handleConnectionClose,
            this::handleConnectionError
        );
    }

    // --- Package-private constructor (testing) ---
    WebSocketRuntimeTransport(ObjectMapper objectMapper, WebSocketClientWrapper wsClient) {
        this.objectMapper = objectMapper;
        this.wsClient = wsClient;
    }

    public void connect() {
        wsClient.connect().join();
    }

    // --- RuntimeCommandTransport ---

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command) {
        return send(command, Duration.ofSeconds(30));
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout) {
        var future = new CompletableFuture<RuntimeAckEnvelope>();
        pendingAcks.put(command.messageId(), future);

        try {
            String json = objectMapper.writeValueAsString(command);
            wsClient.send(json).join();
        } catch (Exception e) {
            pendingAcks.remove(command.messageId());
            throw new RuntimeTransportException("Failed to send command via WebSocket", e, true);
        }

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingAcks.remove(command.messageId());
            throw new RuntimeTransportException(
                "Ack timeout after " + timeout.toMillis() + "ms", e, true);
        } catch (Exception e) {
            pendingAcks.remove(command.messageId());
            throw new RuntimeTransportException("Error waiting for ack", e, false);
        }
    }

    // --- RuntimeEventStreamTransport ---

    @Override
    public SubscriptionHandle subscribe(RuntimeEventSink sink) {
        long id = sinkIdCounter.incrementAndGet();
        SinkRegistration reg = new SinkRegistration(id, sink);
        activeSinks.add(reg);
        return reg;
    }

    // --- Unified incoming message handler ---

    void handleIncoming(String message) {
        try {
            var tree = objectMapper.readTree(message);
            String kind = tree.has("kind") ? tree.get("kind").asText() : null;
            if (kind == null) return;

            RuntimeEnvelopeKind envelopeKind;
            try {
                envelopeKind = RuntimeEnvelopeKind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                return;
            }

            switch (envelopeKind) {
                case ACK, NACK -> {
                    var ack = objectMapper.treeToValue(tree, RuntimeAckEnvelope.class);
                    var future = pendingAcks.remove(ack.correlationId());
                    if (future != null) {
                        future.complete(ack);
                    }
                }
                case EVENT -> {
                    var eventTree = ((com.fasterxml.jackson.databind.node.ObjectNode) tree);
                    eventTree.remove("kind");
                    var event = objectMapper.treeToValue(eventTree, RuntimeRawEvent.class);
                    for (SinkRegistration reg : activeSinks) {
                        reg.sink.onEvent(event);
                    }
                }
                default -> {} // COMMAND frames from server ignored
            }
        } catch (Exception e) {
            for (SinkRegistration reg : activeSinks) {
                reg.sink.onError(new RuntimeTransportException(
                    "Malformed frame ignored", e, false));
            }
        }
    }

    void handleConnectionClose() {
        for (SinkRegistration reg : activeSinks) {
            reg.sink.onClose();
        }
        activeSinks.clear();
        for (var entry : pendingAcks.entrySet()) {
            entry.getValue().completeExceptionally(
                new RuntimeTransportException("Connection closed"));
        }
        pendingAcks.clear();
    }

    void handleConnectionError(Throwable error) {
        var ex = new RuntimeTransportException("WebSocket connection error", error, true);
        for (SinkRegistration reg : activeSinks) {
            reg.sink.onError(ex);
        }
        for (var entry : pendingAcks.entrySet()) {
            entry.getValue().completeExceptionally(
                new RuntimeTransportException("Connection error, pending command aborted", error, true));
        }
        pendingAcks.clear();
    }

    public void close() {
        wsClient.close();
        pendingAcks.clear();
        activeSinks.clear();
    }

    // --- Inner class: SinkRegistration implements SubscriptionHandle ---

    private class SinkRegistration implements SubscriptionHandle {
        private final long id;
        final RuntimeEventSink sink;
        private volatile boolean active = true;

        SinkRegistration(long id, RuntimeEventSink sink) {
            this.id = id;
            this.sink = sink;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            activeSinks.remove(this);
            sink.onClose();
        }
    }
}
