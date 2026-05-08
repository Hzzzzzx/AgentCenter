package com.agentcenter.bridge.application.runtime.transport.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventStreamTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSocketTransportContractTest {

    private static final RuntimeType RUNTIME_TYPE = RuntimeType.OPENCODE;

    private FakeWebSocketRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new FakeWebSocketRuntime(RUNTIME_TYPE);
    }

    @AfterEach
    void tearDown() {
        // no-op; FakeWebSocketRuntime has no resources to release
    }

    @Test
    void sessionEnsure_returnsMatchingAck() {
        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-1",
            JsonNodeFactory.instance.objectNode());

        RuntimeCommandTransport transport = runtime;
        var ack = transport.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
    }

    @Test
    void sessionEnsure_emitsStatusChangedEvent() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-2",
            JsonNodeFactory.instance.objectNode());

        runtime.send(command);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
        assertThat(events.get(0).runtimeSessionId()).isEqualTo("sess-2");
    }

    @Test
    void conversationMessageSend_emitsDeltaThenCompleted() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RUNTIME_TYPE, "sess-3",
            JsonNodeFactory.instance.objectNode().put("text", "hello"));

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.CONVERSATION_DELTA);
        assertThat(events.get(0).rawJson().get("text").asText()).isEqualTo("Echo: hello");
        assertThat(events.get(1).rawType()).isEqualTo(RuntimeEventTypes.CONVERSATION_COMPLETED);
    }

    @Test
    void conversationCancel_emitsRuntimeError() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.CONVERSATION_CANCEL, RUNTIME_TYPE, "sess-4",
            JsonNodeFactory.instance.objectNode());

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.RUNTIME_ERROR);
    }

    @Test
    void unknownCommand_returnsNack() {
        var command = RuntimeCommandEnvelope.of(
            "unknown.command", RUNTIME_TYPE, "sess-5",
            JsonNodeFactory.instance.objectNode());

        var ack = runtime.send(command);

        assertThat(ack.success()).isFalse();
        assertThat(ack.message()).contains("Unknown command type");
    }

    @Test
    void subscribe_handleIsActive_untilClosed() {
        var handle = runtime.subscribe(new EventCollectorSink(new ArrayList<>()));

        assertThat(handle.isActive()).isTrue();

        handle.close();

        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void sendAfterUnsubscribe_noEventsReceived() {
        List<String> closeCalls = new ArrayList<>();
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        RuntimeEventSink trackingSink = new EventCollectorSink(events) {
            @Override
            public void onClose() {
                closeCalls.add("closed");
            }
        };

        var handle = runtime.subscribe(trackingSink);
        handle.close();

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-6",
            JsonNodeFactory.instance.objectNode());
        runtime.send(command);

        assertThat(events).isEmpty();
        assertThat(closeCalls).containsExactly("closed");
    }

    @Test
    void multipleSinks_allReceiveEvents() {
        List<RuntimeRawEvent> eventsA = Collections.synchronizedList(new ArrayList<>());
        List<RuntimeRawEvent> eventsB = Collections.synchronizedList(new ArrayList<>());

        runtime.subscribe(new EventCollectorSink(eventsA));
        runtime.subscribe(new EventCollectorSink(eventsB));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SESSION_ENSURE, RUNTIME_TYPE, "sess-7",
            JsonNodeFactory.instance.objectNode());
        runtime.send(command);

        assertThat(eventsA).hasSize(1);
        assertThat(eventsB).hasSize(1);
        assertThat(eventsA.get(0).rawType()).isEqualTo(RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
        assertThat(eventsB.get(0).rawType()).isEqualTo(RuntimeEventTypes.RUNTIME_STATUS_CHANGED);
    }

    @Test
    void skillInstall_emitsCompletedEvent() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SKILL_INSTALL, RUNTIME_TYPE, "sess-sk1",
            JsonNodeFactory.instance.objectNode().put("skillName", "my-skill"));

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.SKILL_INSTALL_COMPLETED);
        assertThat(events.get(0).runtimeSessionId()).isEqualTo("sess-sk1");
        assertThat(events.get(0).rawJson().get("skillName").asText()).isEqualTo("my-skill");
        assertThat(events.get(0).rawJson().get("status").asText()).isEqualTo("installed");
    }

    @Test
    void skillDelete_emitsCompletedEvent() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SKILL_DELETE, RUNTIME_TYPE, "sess-sk2",
            JsonNodeFactory.instance.objectNode().put("skillName", "old-skill"));

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.SKILL_DELETE_COMPLETED);
        assertThat(events.get(0).runtimeSessionId()).isEqualTo("sess-sk2");
        assertThat(events.get(0).rawJson().get("skillName").asText()).isEqualTo("old-skill");
        assertThat(events.get(0).rawJson().get("status").asText()).isEqualTo("deleted");
    }

    @Test
    void mcpRefresh_emitsCompletedEvent() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.MCP_REFRESH, RUNTIME_TYPE, "sess-mcp1",
            JsonNodeFactory.instance.objectNode());

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.MCP_REFRESH_COMPLETED);
        assertThat(events.get(0).runtimeSessionId()).isEqualTo("sess-mcp1");
        assertThat(events.get(0).rawJson().get("status").asText()).isEqualTo("refreshed");
    }

    @Test
    void skillRun_emitsStartedThenCompleted() {
        List<RuntimeRawEvent> events = Collections.synchronizedList(new ArrayList<>());
        runtime.subscribe(new EventCollectorSink(events));

        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.SKILL_RUN, RUNTIME_TYPE, "sess-sr1",
            JsonNodeFactory.instance.objectNode().put("skillName", "run-skill"));

        var ack = runtime.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).rawType()).isEqualTo(RuntimeEventTypes.SKILL_RUN_STARTED);
        assertThat(events.get(0).rawJson().get("skillName").asText()).isEqualTo("run-skill");
        assertThat(events.get(1).rawType()).isEqualTo(RuntimeEventTypes.SKILL_RUN_COMPLETED);
        assertThat(events.get(1).rawJson().get("skillName").asText()).isEqualTo("run-skill");
        assertThat(events.get(1).rawJson().get("status").asText()).isEqualTo("completed");
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
}
