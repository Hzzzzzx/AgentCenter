package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.translation.AssistantMessageProjector;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventEnvelopeDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests the glue path in OpenCodeEventSubscriber: raw SSE JSON →
 * extractSessionId → RuntimeRawEvent → translator → dispatcher.
 */
class OpenCodeEventSubscriberGlueTest {

    private RuntimeEventEnvelopeDispatcher dispatcher;
    private AssistantMessageProjector projector;
    private OpenCodeEventSubscriber subscriber;
    private ObjectMapper objectMapper;

    private static final String OPENCODE_SES = "oc_ses_1";
    private static final String AGENT_SES = "agent_ses_1";

    @BeforeEach
    void setUp() {
        dispatcher = mock(RuntimeEventEnvelopeDispatcher.class);
        projector = mock(AssistantMessageProjector.class);
        objectMapper = new ObjectMapper();

        subscriber = new OpenCodeEventSubscriber(objectMapper, dispatcher, projector);
        subscriber.registerSession(OPENCODE_SES, AGENT_SES, "http://unused", "/unused");
    }

    /**
     * Directly invoke normalizeAndPublish via reflection to test the glue path.
     */
    private void invokeNormalizeAndPublish(String json) throws Exception {
        var method = OpenCodeEventSubscriber.class.getDeclaredMethod("normalizeAndPublish",
            com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        method.invoke(subscriber, objectMapper.readTree(json));
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

        // Dispatcher should receive envelopes
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

        // session.idle → status changed + conversation completed
        verify(dispatcher).dispatch(argThat(envelopes ->
            envelopes != null && envelopes.size() == 2
            && RuntimeEventTypes.RUNTIME_STATUS_CHANGED.equals(envelopes.get(0).type())
            && RuntimeEventTypes.CONVERSATION_COMPLETED.equals(envelopes.get(1).type())
        ));
    }

    @Test
    void sessionExtractedFromPartPath() throws Exception {
        // Session ID nested under properties.part.sessionID
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
}
