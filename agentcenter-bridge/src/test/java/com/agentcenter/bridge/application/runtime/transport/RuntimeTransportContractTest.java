package com.agentcenter.bridge.application.runtime.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RuntimeTransportContractTest {

    private static final RuntimeType RUNTIME_TYPE = RuntimeType.OPENCODE;

    @Test
    void commandTransport_send_returnsMatchingAck() {
        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RUNTIME_TYPE, "session-1",
            JsonNodeFactory.instance.objectNode().put("text", "hello"));

        RuntimeCommandTransport transport = stubTransport(
            cmd -> RuntimeAckEnvelope.ack(cmd.messageId(), RUNTIME_TYPE));

        var ack = transport.send(command);

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
    }

    @Test
    void commandTransport_sendWithTimeout_overloadWorks() {
        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RUNTIME_TYPE, "session-1",
            JsonNodeFactory.instance.objectNode());

        RuntimeCommandTransport transport = stubTransport(
            cmd -> RuntimeAckEnvelope.ack(cmd.messageId(), RUNTIME_TYPE));

        var ack = transport.send(command, Duration.ofSeconds(5));

        assertThat(ack.success()).isTrue();
        assertThat(ack.correlationId()).isEqualTo(command.messageId());
    }

    @Test
    void commandTransport_send_returnsNackOnFailure() {
        var command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RUNTIME_TYPE, "session-1",
            JsonNodeFactory.instance.objectNode());

        RuntimeCommandTransport transport = stubTransport(
            cmd -> RuntimeAckEnvelope.nack(cmd.messageId(), RUNTIME_TYPE, "runtime busy"));

        var ack = transport.send(command);

        assertThat(ack.success()).isFalse();
        assertThat(ack.message()).isEqualTo("runtime busy");
    }

    @Test
    void eventStreamTransport_subscribe_returnsActiveHandle() {
        RuntimeEventStreamTransport transport = sink -> new SimpleSubscriptionHandle();

        var handle = transport.subscribe(new StubEventSink());

        assertThat(handle.isActive()).isTrue();
        handle.close();
    }

    @Test
    void subscriptionHandle_close_deactivatesSubscription() {
        var handle = new SimpleSubscriptionHandle();

        assertThat(handle.isActive()).isTrue();

        handle.close();

        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void eventSink_onEvent_receivesEvents() {
        List<RuntimeRawEvent> received = new ArrayList<>();
        RuntimeEventSink sink = new StubEventSink() {
            @Override
            public void onEvent(RuntimeRawEvent event) {
                received.add(event);
            }
        };

        var event = new RuntimeRawEvent(RUNTIME_TYPE, "ASSISTANT_DELTA",
            JsonNodeFactory.instance.objectNode(), "session-1");

        sink.onEvent(event);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).rawType()).isEqualTo("ASSISTANT_DELTA");
    }

    @Test
    void eventSink_onError_receivesTransportErrors() {
        List<RuntimeTransportException> errors = new ArrayList<>();
        RuntimeEventSink sink = new StubEventSink() {
            @Override
            public void onError(RuntimeTransportException error) {
                errors.add(error);
            }
        };

        var error = new RuntimeTransportException("connection lost", new IOException("reset"), true);
        sink.onError(error);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).isEqualTo("connection lost");
    }

    @Test
    void transportException_storesRecoverableFlag() {
        var ex = new RuntimeTransportException("timeout", new IOException("timed out"), true);

        assertThat(ex.isRecoverable()).isTrue();
        assertThat(ex.getMessage()).isEqualTo("timeout");
    }

    @Test
    void transportException_withCause_preservesCause() {
        var cause = new IOException("connection refused");
        var ex = new RuntimeTransportException("transport failed", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.isRecoverable()).isFalse();
    }

    private static RuntimeCommandTransport stubTransport(
        Function<RuntimeCommandEnvelope, RuntimeAckEnvelope> sender) {
        BiFunction<RuntimeCommandEnvelope, Duration, RuntimeAckEnvelope> timeoutSender =
            (cmd, timeout) -> sender.apply(cmd);
        return new RuntimeCommandTransport() {
            @Override
            public RuntimeAckEnvelope send(RuntimeCommandEnvelope command) {
                return sender.apply(command);
            }
            @Override
            public RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout) {
                return timeoutSender.apply(command, timeout);
            }
        };
    }

    private static class StubEventSink implements RuntimeEventSink {
        @Override public void onEvent(RuntimeRawEvent event) {}
        @Override public void onError(RuntimeTransportException error) {}
        @Override public void onClose() {}
    }

    private static class SimpleSubscriptionHandle implements SubscriptionHandle {
        private boolean active = true;

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
        }
    }
}
