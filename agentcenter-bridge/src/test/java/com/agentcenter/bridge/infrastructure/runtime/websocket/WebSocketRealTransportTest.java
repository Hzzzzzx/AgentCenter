package com.agentcenter.bridge.infrastructure.runtime.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentcenter.bridge.application.runtime.protocol.*;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WebSocketRealTransportTest {

    private static final RuntimeType RUNTIME_TYPE = RuntimeType.OPENCODE;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    // --- Command transport tests ---

    @Test
    void commandTransport_ackMatchingCompletesPendingFuture() throws Exception {
        List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());
        StubWsClient stub = new StubWsClient(sentMessages);
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, stub);

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-ack-1",
            JsonNodeFactory.instance.objectNode());

        CompletableFuture<Void> injectAck = CompletableFuture.runAsync(() -> {
            sleep(50);
            RuntimeAckEnvelope ack = RuntimeAckEnvelope.ack(command.messageId(), RUNTIME_TYPE);
            transport.handleIncoming(toJson(ack));
        });

        RuntimeAckEnvelope result = transport.send(command, Duration.ofSeconds(5));
        injectAck.join();

        assertThat(result.success()).isTrue();
        assertThat(result.correlationId()).isEqualTo(command.messageId());
        assertThat(sentMessages).hasSize(1);

        JsonNode sent = OBJECT_MAPPER.readTree(sentMessages.get(0));
        assertThat(sent.get("type").asText()).isEqualTo("session.ensure");
        assertThat(sent.get("kind").asText()).isEqualTo("COMMAND");

        transport.close();
    }

    @Test
    void commandTransport_nackIsDeliveredCorrectly() {
        List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(sentMessages));

        var command = RuntimeCommandEnvelope.of(
            "unknown.cmd", RUNTIME_TYPE, "sess-nack-1",
            JsonNodeFactory.instance.objectNode());

        CompletableFuture<Void> injectFuture = CompletableFuture.runAsync(() -> {
            sleep(50);
            RuntimeAckEnvelope nack = RuntimeAckEnvelope.nack(command.messageId(), RUNTIME_TYPE, "not supported");
            transport.handleIncoming(toJson(nack));
        });

        RuntimeAckEnvelope result = transport.send(command, Duration.ofSeconds(5));
        injectFuture.join();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("not supported");
        assertThat(result.correlationId()).isEqualTo(command.messageId());

        transport.close();
    }

    @Test
    void commandTransport_timeoutWhenNoAck() {
        List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(sentMessages));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-timeout",
            JsonNodeFactory.instance.objectNode());

        assertThatThrownBy(() -> transport.send(command, Duration.ofMillis(100)))
            .isInstanceOf(RuntimeTransportException.class)
            .hasMessageContaining("Ack timeout");

        transport.close();
    }

    @Test
    void commandTransport_malformedAckIsIgnored() {
        List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(sentMessages));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-malformed",
            JsonNodeFactory.instance.objectNode());

        CompletableFuture<Void> injectFuture = CompletableFuture.runAsync(() -> {
            sleep(50);
            transport.handleIncoming("not valid json!!!");
            transport.handleIncoming("{\"kind\":\"EVENT\"}");
            RuntimeAckEnvelope ack = RuntimeAckEnvelope.ack(command.messageId(), RUNTIME_TYPE);
            transport.handleIncoming(toJson(ack));
        });

        RuntimeAckEnvelope result = transport.send(command, Duration.ofSeconds(5));
        injectFuture.join();

        assertThat(result.success()).isTrue();
        assertThat(result.correlationId()).isEqualTo(command.messageId());

        transport.close();
    }

    // --- Event stream tests ---

    @Test
    void eventFrame_deliveredToAllSubscribers() {
        List<RuntimeRawEvent> eventsA = Collections.synchronizedList(new ArrayList<>());
        List<RuntimeRawEvent> eventsB = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        transport.subscribe(new EventCollectorSink(eventsA));
        transport.subscribe(new EventCollectorSink(eventsB));

        String eventJson = """
            {
              "kind": "EVENT",
              "runtimeType": "OPENCODE",
              "rawType": "skill.install.completed",
              "rawJson": {"skillName": "my-skill", "status": "installed"},
              "runtimeSessionId": "sess-ev-1"
            }
            """;
        transport.handleIncoming(eventJson);

        assertThat(eventsA).hasSize(1);
        assertThat(eventsB).hasSize(1);
        assertThat(eventsA.get(0).rawType()).isEqualTo("skill.install.completed");
        assertThat(eventsB.get(0).rawType()).isEqualTo("skill.install.completed");

        transport.close();
    }

    @Test
    void eventFrame_parsedCorrectly() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        transport.subscribe(new EventCollectorSink(events));

        String eventJson = """
            {
              "kind": "EVENT",
              "runtimeType": "OPENCODE",
              "rawType": "conversation.delta",
              "rawJson": {"text": "hello world"},
              "runtimeSessionId": "sess-ev-2"
            }
            """;
        transport.handleIncoming(eventJson);

        assertThat(events).hasSize(1);
        RuntimeRawEvent event = events.get(0);
        assertThat(event.runtimeType()).isEqualTo(RUNTIME_TYPE);
        assertThat(event.rawType()).isEqualTo("conversation.delta");
        assertThat(event.rawJson().get("text").asText()).isEqualTo("hello world");
        assertThat(event.runtimeSessionId()).isEqualTo("sess-ev-2");

        transport.close();
    }

    @Test
    void connectionClose_notifiesAllSinks() {
        List<String> closeCalls = Collections.synchronizedList(new ArrayList<>());
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        transport.subscribe(new EventCollectorSink(events) {
            @Override
            public void onClose() {
                closeCalls.add("closed-A");
            }
        });
        transport.subscribe(new EventCollectorSink(events) {
            @Override
            public void onClose() {
                closeCalls.add("closed-B");
            }
        });

        transport.handleConnectionClose();

        assertThat(closeCalls).containsExactlyInAnyOrder("closed-A", "closed-B");

        transport.close();
    }

    @Test
    void connectionError_notifiesAllSinks() {
        List<RuntimeTransportException> errors = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        transport.subscribe(new EventCollectorSink(new ArrayList<>()) {
            @Override
            public void onError(RuntimeTransportException error) {
                errors.add(error);
            }
        });
        transport.subscribe(new EventCollectorSink(new ArrayList<>()) {
            @Override
            public void onError(RuntimeTransportException error) {
                errors.add(error);
            }
        });

        transport.handleConnectionError(new RuntimeException("connection lost"));

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getMessage()).contains("WebSocket connection error");
        assertThat(errors.get(0).isRecoverable()).isTrue();

        transport.close();
    }

    @Test
    void connectionError_failsPendingAcksImmediately() {
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-err-pending",
            JsonNodeFactory.instance.objectNode());

        CompletableFuture<Void> injectFuture = CompletableFuture.runAsync(() -> {
            sleep(50);
            transport.handleConnectionError(new RuntimeException("connection lost"));
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> transport.send(command, Duration.ofSeconds(5)))
            .isInstanceOf(RuntimeTransportException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(2000);
        injectFuture.join();

        transport.close();
    }

    @Test
    void malformedFrame_notifiesSinksWithError() {
        List<RuntimeTransportException> errors = Collections.synchronizedList(new ArrayList<>());
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        transport.subscribe(new EventCollectorSink(events) {
            @Override
            public void onError(RuntimeTransportException error) {
                errors.add(error);
            }
        });

        transport.handleIncoming("not valid json!!!");

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("Malformed frame ignored");
        assertThat(errors.get(0).isRecoverable()).isFalse();

        transport.close();
    }

    // --- Serialization round-trip tests ---

    @Test
    void commandEnvelope_serializationRoundTrip() throws Exception {
        var original = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SKILL_INSTALL, RUNTIME_TYPE, "sess-ser",
            JsonNodeFactory.instance.objectNode().put("skillName", "test-skill"));

        String json = OBJECT_MAPPER.writeValueAsString(original);
        JsonNode tree = OBJECT_MAPPER.readTree(json);

        assertThat(tree.get("kind").asText()).isEqualTo("COMMAND");
        assertThat(tree.get("type").asText()).isEqualTo("skill.install");
        assertThat(tree.get("runtimeType").asText()).isEqualTo("OPENCODE");
        assertThat(tree.get("runtimeSessionId").asText()).isEqualTo("sess-ser");
        assertThat(tree.get("payload").get("skillName").asText()).isEqualTo("test-skill");
    }

    @Test
    void ackEnvelope_serializationRoundTrip() throws Exception {
        var ack = RuntimeAckEnvelope.ack("corr-123", RUNTIME_TYPE);
        String json = OBJECT_MAPPER.writeValueAsString(ack);
        var deserialized = OBJECT_MAPPER.readValue(json, RuntimeAckEnvelope.class);

        assertThat(deserialized.kind()).isEqualTo(RuntimeEnvelopeKind.ACK);
        assertThat(deserialized.success()).isTrue();
        assertThat(deserialized.correlationId()).isEqualTo("corr-123");
        assertThat(deserialized.runtimeType()).isEqualTo(RUNTIME_TYPE);
    }

    @Test
    void nackEnvelope_serializationRoundTrip() throws Exception {
        var nack = RuntimeAckEnvelope.nack("corr-456", RUNTIME_TYPE, "something went wrong");
        String json = OBJECT_MAPPER.writeValueAsString(nack);
        var deserialized = OBJECT_MAPPER.readValue(json, RuntimeAckEnvelope.class);

        assertThat(deserialized.kind()).isEqualTo(RuntimeEnvelopeKind.NACK);
        assertThat(deserialized.success()).isFalse();
        assertThat(deserialized.correlationId()).isEqualTo("corr-456");
        assertThat(deserialized.message()).isEqualTo("something went wrong");
    }

    @Test
    void rawEvent_deserializationFromJson() throws Exception {
        String json = """
            {
              "runtimeType": "OPENCODE",
              "rawType": "skill.install.completed",
              "rawJson": {"skillName": "my-skill", "status": "installed"},
              "runtimeSessionId": "sess-event-1"
            }
            """;

        RuntimeRawEvent event = OBJECT_MAPPER.readValue(json, RuntimeRawEvent.class);

        assertThat(event.runtimeType()).isEqualTo(RUNTIME_TYPE);
        assertThat(event.rawType()).isEqualTo("skill.install.completed");
        assertThat(event.rawJson().get("skillName").asText()).isEqualTo("my-skill");
        assertThat(event.runtimeSessionId()).isEqualTo("sess-event-1");
    }

    @Test
    void allCommandTypes_areHandledByCommandTransportSerialization() throws Exception {
        List<String> commandTypes = Stream.of(
            RuntimeCommandTypes.SESSION_ENSURE,
            RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND,
            RuntimeCommandTypes.CONVERSATION_CANCEL,
            RuntimeCommandTypes.SKILL_INSTALL,
            RuntimeCommandTypes.SKILL_DELETE,
            RuntimeCommandTypes.SKILL_RUN,
            RuntimeCommandTypes.MCP_REFRESH
        ).toList();

        for (String cmdType : commandTypes) {
            var envelope = RuntimeCommandEnvelope.of(cmdType, RUNTIME_TYPE, "sess-all",
                JsonNodeFactory.instance.objectNode());
            String json = OBJECT_MAPPER.writeValueAsString(envelope);
            JsonNode tree = OBJECT_MAPPER.readTree(json);

            assertThat(tree.get("type").asText())
                .as("Command type %s should serialize correctly", cmdType)
                .isEqualTo(cmdType);
            assertThat(tree.get("kind").asText()).isEqualTo("COMMAND");
        }
    }

    // --- Subscription lifecycle ---

    @Test
    void subscription_isActiveUntilClosed() {
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));
        SubscriptionHandle handle = transport.subscribe(new EventCollectorSink(new ArrayList<>()));

        assertThat(handle.isActive()).isTrue();
        handle.close();
        assertThat(handle.isActive()).isFalse();

        transport.close();
    }

    @Test
    void closedSubscription_doesNotReceiveEvents() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        WebSocketRuntimeTransport transport = new WebSocketRuntimeTransport(OBJECT_MAPPER, new StubWsClient(new ArrayList<>()));

        SubscriptionHandle handle = transport.subscribe(new EventCollectorSink(events));
        handle.close();

        String eventJson = """
            {
              "kind": "EVENT",
              "runtimeType": "OPENCODE",
              "rawType": "test.event",
              "rawJson": {},
              "runtimeSessionId": "sess-no"
            }
            """;
        transport.handleIncoming(eventJson);

        assertThat(events).isEmpty();

        transport.close();
    }

    // --- Stub and helpers ---

    static class StubWsClient extends WebSocketClientWrapper {
        private final List<String> sentMessages;

        StubWsClient(List<String> sentMessages) {
            super(URI.create("ws://localhost:1"), msg -> {});
            this.sentMessages = sentMessages;
        }

        @Override
        public CompletableFuture<java.net.http.WebSocket> send(String message) {
            sentMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> connect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {}
    }

    private static class EventCollectorSink implements RuntimeEventSink {
        private final List<RuntimeRawEvent> events;

        EventCollectorSink(List<RuntimeRawEvent> events) {
            this.events = events;
        }

        @Override
        public void onEvent(RuntimeRawEvent event) {
            events.add(event);
        }

        @Override
        public void onError(RuntimeTransportException error) {}

        @Override
        public void onClose() {}
    }

    private static void sleep(int millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String toJson(Object value) {
        try { return OBJECT_MAPPER.writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
