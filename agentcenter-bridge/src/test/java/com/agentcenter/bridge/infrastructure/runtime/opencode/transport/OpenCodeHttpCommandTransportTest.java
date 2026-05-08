package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class OpenCodeHttpCommandTransportTest {

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private ObjectMapper objectMapper;
    private OpenCodeHttpCommandTransport transport;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        objectMapper = new ObjectMapper();
        transport = new OpenCodeHttpCommandTransport(httpClient, objectMapper);

        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(200);
    }

    private ObjectNode createSessionPayload(String baseUrl, String cwd) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd);
        payload.put("title", "Test Session");
        ArrayNode perms = payload.putArray("permission");
        ObjectNode perm = perms.addObject();
        perm.put("permission", "edit");
        perm.put("pattern", "*");
        perm.put("action", "ask");
        return payload;
    }

    @Test
    void sessionCreateReturnsAckWithSessionId() throws Exception {
        when(httpResponse.body()).thenReturn("{\"id\":\"ses_abc123\"}");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeAckEnvelope ack = transport.send(command);

        assertTrue(ack.success());
        assertEquals("ses_abc123", ack.payload().path("sessionId").asText(""));
        assertEquals(command.messageId(), ack.correlationId());
    }

    @Test
    void sessionCreateReturnsNackWhenIdMissing() throws Exception {
        when(httpResponse.body()).thenReturn("{}");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeAckEnvelope ack = transport.send(command);

        assertFalse(ack.success());
        assertTrue(ack.message().contains("missing id"));
    }

    @Test
    void conversationSendReturnsAck() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", "http://localhost:4097");
        payload.put("workingDirectory", "/tmp/work");
        payload.put("agent", "build");
        ArrayNode parts = payload.putArray("parts");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", "hello");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RuntimeType.OPENCODE,
                "ses_abc123", payload);

        RuntimeAckEnvelope ack = transport.send(command);

        assertTrue(ack.success());
    }

    @Test
    void conversationSendReturnsNackWithoutSessionId() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", "http://localhost:4097");
        payload.put("workingDirectory", "/tmp/work");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RuntimeType.OPENCODE,
                null, payload);

        RuntimeAckEnvelope ack = transport.send(command);

        assertFalse(ack.success());
        assertTrue(ack.message().contains("runtimeSessionId is required"));
    }

    @Test
    void conversationCancelReturnsAckWithoutHttp() {
        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_CANCEL, RuntimeType.OPENCODE,
                "ses_abc123", objectMapper.createObjectNode());

        RuntimeAckEnvelope ack = transport.send(command);

        assertTrue(ack.success());
    }

    @Test
    void unknownCommandTypeReturnsNack() {
        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                "unknown.type", RuntimeType.OPENCODE, null, objectMapper.createObjectNode());

        RuntimeAckEnvelope ack = transport.send(command);

        assertFalse(ack.success());
        assertTrue(ack.message().contains("Unsupported command type"));
    }

    @Test
    void http500ThrowsRecoverable() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeTransportException ex = assertThrows(RuntimeTransportException.class,
                () -> transport.send(command));
        assertTrue(ex.isRecoverable());
    }

    @Test
    void http400ThrowsNonRecoverable() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("Bad Request");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeTransportException ex = assertThrows(RuntimeTransportException.class,
                () -> transport.send(command));
        assertFalse(ex.isRecoverable());
    }

    @Test
    void ioExceptionThrowsRecoverable() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused"));

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeTransportException ex = assertThrows(RuntimeTransportException.class,
                () -> transport.send(command));
        assertTrue(ex.isRecoverable());
    }

    @Test
    void sendWithCustomTimeout() throws Exception {
        when(httpResponse.body()).thenReturn("{\"id\":\"ses_timeout\"}");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null,
                createSessionPayload("http://localhost:4097", "/tmp/work"));

        RuntimeAckEnvelope ack = transport.send(command, Duration.ofSeconds(60));

        assertTrue(ack.success());
    }

    @Test
    void fetchMessagesReturnsJsonWhenOk() throws Exception {
        when(httpResponse.body()).thenReturn("[{\"info\":{\"id\":\"msg_1\"}}]");
        when(httpResponse.statusCode()).thenReturn(200);

        var result = transport.fetchMessages("http://localhost:4097", "/tmp/work", "ses_123");

        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals("msg_1", result.get(0).path("info").path("id").asText(""));
    }

    @Test
    void fetchMessagesReturnsNullOnFailure() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);

        var result = transport.fetchMessages("http://localhost:4097", "/tmp/work", "ses_123");

        assertNull(result);
    }

    @Test
    void fetchMessagesReturnsNullOnException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection reset"));

        var result = transport.fetchMessages("http://localhost:4097", "/tmp/work", "ses_123");

        assertNull(result);
    }
}
