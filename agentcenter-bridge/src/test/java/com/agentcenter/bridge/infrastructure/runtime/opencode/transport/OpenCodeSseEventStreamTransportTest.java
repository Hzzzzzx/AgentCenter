package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

class OpenCodeSseEventStreamTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void subscribeReturnsActiveHandle() {
        OpenCodeSseEventStreamTransport transport = newTransport();

        SubscriptionHandle handle = transport.subscribe(mock(RuntimeEventSink.class));
        assertTrue(handle.isActive());
        handle.close();
        assertFalse(handle.isActive());
        transport.shutdown();
    }

    @Test
    void closeHandleIsIdempotent() {
        OpenCodeSseEventStreamTransport transport = newTransport();

        SubscriptionHandle handle = transport.subscribe(mock(RuntimeEventSink.class));
        handle.close();
        handle.close();
        assertFalse(handle.isActive());
        transport.shutdown();
    }

    @Test
    void parseAndDeliverExtractsSessionIdFromProperties() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeRawEvent> captured = new AtomicReference<>();

        OpenCodeSseEventStreamTransport transport = newTransport();

        ObjectNode eventJson = JsonNodeFactory.instance.objectNode();
        eventJson.put("type", "session.status");
        ObjectNode props = eventJson.putObject("properties");
        props.put("sessionID", "ses_abc");
        props.put("status", "busy");

        invokeParseAndDeliver(transport, "data: " + objectMapper.writeValueAsString(eventJson),
            capturingSink(captured, latch));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        RuntimeRawEvent raw = captured.get();
        assertNotNull(raw);
        assertEquals(RuntimeType.OPENCODE, raw.runtimeType());
        assertEquals("session.status", raw.rawType());
        assertEquals("ses_abc", raw.runtimeSessionId());

        transport.shutdown();
    }

    @Test
    void parseAndDeliverIgnoresDone() throws Exception {
        AtomicReference<RuntimeRawEvent> captured = new AtomicReference<>();

        OpenCodeSseEventStreamTransport transport = newTransport();
        invokeParseAndDeliver(transport, "data: [DONE]",
            capturingSink(captured, null));

        assertNull(captured.get());
        transport.shutdown();
    }

    @Test
    void parseAndDeliverIgnoresNonJson() throws Exception {
        AtomicReference<RuntimeRawEvent> captured = new AtomicReference<>();

        OpenCodeSseEventStreamTransport transport = newTransport();
        invokeParseAndDeliver(transport, "data: this is not json at all",
            capturingSink(captured, null));

        assertNull(captured.get());
        transport.shutdown();
    }

    @Test
    void parseAndDeliverExtractsSessionIdFromNestedPaths() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeRawEvent> captured = new AtomicReference<>();

        OpenCodeSseEventStreamTransport transport = newTransport();

        ObjectNode eventJson = JsonNodeFactory.instance.objectNode();
        eventJson.put("type", "message.part.delta");
        ObjectNode props = eventJson.putObject("properties");
        ObjectNode part = props.putObject("part");
        part.put("sessionID", "ses_nested");
        part.put("type", "text");
        part.put("id", "p1");
        props.put("delta", "hello");

        invokeParseAndDeliver(transport, "data: " + objectMapper.writeValueAsString(eventJson),
            capturingSink(captured, latch));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        RuntimeRawEvent raw = captured.get();
        assertNotNull(raw);
        assertEquals("ses_nested", raw.runtimeSessionId());
        assertEquals("message.part.delta", raw.rawType());

        transport.shutdown();
    }

    private OpenCodeSseEventStreamTransport newTransport() {
        OpenCodeSseEventStreamTransport transport = new OpenCodeSseEventStreamTransport(
            java.net.http.HttpClient.newBuilder().build(),
            objectMapper
        );
        transport.configure("http://127.0.0.1:1", "/tmp");
        return transport;
    }

    private void invokeParseAndDeliver(OpenCodeSseEventStreamTransport transport,
                                       String sseBlock, RuntimeEventSink sink) throws Exception {
        Method method = OpenCodeSseEventStreamTransport.class.getDeclaredMethod(
            "parseAndDeliver", String.class, RuntimeEventSink.class);
        method.setAccessible(true);
        method.invoke(transport, sseBlock, sink);
    }

    private RuntimeEventSink capturingSink(AtomicReference<RuntimeRawEvent> captured,
                                           CountDownLatch latch) {
        return new RuntimeEventSink() {
            @Override public void onEvent(RuntimeRawEvent event) {
                captured.set(event);
                if (latch != null) latch.countDown();
            }
            @Override public void onError(RuntimeTransportException error) {}
            @Override public void onClose() {}
        };
    }
}
