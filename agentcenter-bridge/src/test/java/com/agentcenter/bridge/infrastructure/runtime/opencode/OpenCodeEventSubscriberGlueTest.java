package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.translation.AssistantMessageProjector;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventEnvelopeDispatcher;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeSseEventStreamTransport;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenCodeEventSubscriberGlueTest {

    private RuntimeEventEnvelopeDispatcher dispatcher;
    private AssistantMessageProjector projector;
    private OpenCodeSseEventStreamTransport transport;
    private OpenCodeEventSubscriber subscriber;
    private ObjectMapper objectMapper;

    private static final String OPENCODE_SES = "oc_ses_1";
    private static final String AGENT_SES = "agent_ses_1";

    @BeforeEach
    void setUp() {
        dispatcher = mock(RuntimeEventEnvelopeDispatcher.class);
        projector = mock(AssistantMessageProjector.class);
        objectMapper = new ObjectMapper();

        transport = new OpenCodeSseEventStreamTransport(
            java.net.http.HttpClient.newBuilder().build(), objectMapper);
        transport.configure("http://unused", "/unused");

        subscriber = new OpenCodeEventSubscriber(dispatcher, projector, transport);
        subscriber.registerSession(OPENCODE_SES, AGENT_SES, "http://unused", "/unused");
    }

    @AfterEach
    void tearDown() {
        subscriber.shutdown();
    }

    private void invokeNormalizeAndPublish(String json) throws Exception {
        subscriber.normalizeAndPublish(objectMapper.readTree(json));
    }

    @Test
    void textDeltaGluePathDispatches() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "sessionID": "oc_ses_1",
            "delta": "Hi from glue",
            "part": {"type": "text", "id": "gp1"}
          }
        }
        """;

        invokeNormalizeAndPublish(json);

        verify(dispatcher).dispatch(argThat(envelopes -> {
            if (envelopes == null || envelopes.isEmpty()) return false;
            RuntimeEventEnvelope env = envelopes.get(0);
            return RuntimeEventTypes.CONVERSATION_DELTA.equals(env.type())
                && AGENT_SES.equals(env.agentSessionId())
                && OPENCODE_SES.equals(env.runtimeSessionId())
                && "Hi from glue".equals(env.payload().path("label").asText());
        }));
    }

    @Test
    void unknownSessionNotDispatched() throws Exception {
        String json = """
        {
          "type": "message.part.delta",
          "properties": {
            "sessionID": "unknown_session",
            "delta": "Ignored",
            "part": {"type": "text", "id": "gp2"}
          }
        }
        """;

        invokeNormalizeAndPublish(json);

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void sessionIdleGluePathDispatches() throws Exception {
        String json = """
        {
          "type": "session.idle",
          "properties": {
            "sessionID": "oc_ses_1"
          }
        }
        """;

        invokeNormalizeAndPublish(json);

        verify(dispatcher).dispatch(argThat(envelopes ->
            envelopes != null && envelopes.size() == 3
            && RuntimeEventTypes.RUNTIME_STATUS_CHANGED.equals(envelopes.get(0).type())
            && RuntimeEventTypes.PROCESS_TRACE.equals(envelopes.get(1).type())
            && "node_status".equals(envelopes.get(1).payload().path("kind").asText())
            && "completed".equals(envelopes.get(1).payload().path("status").asText())
            && RuntimeEventTypes.CONVERSATION_COMPLETED.equals(envelopes.get(2).type())
        ));
    }

    @Test
    void sessionExtractedFromPartPath() throws Exception {
        String json = """
        {
          "type": "session.status",
          "properties": {
            "part": {"sessionID": "oc_ses_1"},
            "status": "busy"
          }
        }
        """;

        invokeNormalizeAndPublish(json);

        verify(dispatcher).dispatch(argThat(envelopes ->
            envelopes != null && !envelopes.isEmpty()
            && RuntimeEventTypes.RUNTIME_STATUS_CHANGED.equals(envelopes.get(0).type())
            && "running".equals(envelopes.get(0).payload().path("label").asText())
        ));
    }

    @Test
    void transportErrorDispatchesVisibleRuntimeError() {
        subscriber.onError(new RuntimeTransportException("stream dropped", null, true));

        verify(dispatcher).dispatch(argThat(envelopes -> {
            if (envelopes == null || envelopes.isEmpty()) return false;
            RuntimeEventEnvelope env = envelopes.get(0);
            return RuntimeEventTypes.RUNTIME_ERROR.equals(env.type())
                && env.operationId() == null
                && AGENT_SES.equals(env.agentSessionId())
                && OPENCODE_SES.equals(env.runtimeSessionId())
                && "runtime_connection".equals(env.payload().path("kind").asText())
                && "failed".equals(env.payload().path("status").asText())
                && "event.stream.error".equals(env.payload().path("rawEventType").asText())
                && env.payload().path("recoverable").asBoolean(false);
        }));
    }

    @Test
    void transportCloseDispatchesVisibleDisconnectedStatus() {
        subscriber.onClose();

        verify(dispatcher).dispatch(argThat(envelopes -> {
            if (envelopes == null || envelopes.isEmpty()) return false;
            RuntimeEventEnvelope env = envelopes.get(0);
            return RuntimeEventTypes.RUNTIME_STATUS_CHANGED.equals(env.type())
                && AGENT_SES.equals(env.agentSessionId())
                && OPENCODE_SES.equals(env.runtimeSessionId())
                && "runtime_connection".equals(env.payload().path("kind").asText())
                && "disconnected".equals(env.payload().path("status").asText())
                && "event.stream.closed".equals(env.payload().path("rawEventType").asText());
        }));
    }
}
