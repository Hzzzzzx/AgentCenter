package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;
import com.agentcenter.bridge.application.runtime.translation.AssistantMessageProjector;
import com.agentcenter.bridge.application.runtime.translation.RuntimeEventEnvelopeDispatcher;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeSseEventStreamTransport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Subscribes to the opencode serve SSE event stream via
 * {@link OpenCodeSseEventStreamTransport}, extracts raw events,
 * delegates translation to {@link OpenCodeRuntimeEventTranslator},
 * and dispatches resulting {@link RuntimeEventEnvelope}s via
 * {@link RuntimeEventEnvelopeDispatcher}.
 *
 * <p>A single SSE connection serves all sessions. Events are demultiplexed
 * by extracting the opencode session ID from each event and mapping it
 * back to the agent session ID.</p>
 */
@Component
public class OpenCodeEventSubscriber implements RuntimeTranslationContext, RuntimeEventSink {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeEventSubscriber.class);

    private final OpenCodeTranslationState translationState;
    private final OpenCodeRuntimeEventTranslator translator;
    private final RuntimeEventEnvelopeDispatcher dispatcher;
    private final AssistantMessageProjector projector;
    private final OpenCodeSseEventStreamTransport transport;

    private final Map<String, String> opencodeToAgentSession = new ConcurrentHashMap<>();
    private final Map<String, WorkflowContext> agentSessionToWorkflow = new ConcurrentHashMap<>();

    private SubscriptionHandle subscriptionHandle;

    public OpenCodeEventSubscriber(RuntimeEventEnvelopeDispatcher dispatcher,
                                   AssistantMessageProjector projector,
                                   OpenCodeSseEventStreamTransport transport) {
        this.translationState = new OpenCodeTranslationState();
        this.translator = new OpenCodeRuntimeEventTranslator(translationState);
        this.dispatcher = dispatcher;
        this.projector = projector;
        this.transport = transport;
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

    @Override
    public String getWorkflowNodeInstanceId(String agentSessionId) {
        WorkflowContext ctx = agentSessionToWorkflow.get(agentSessionId);
        return ctx != null ? ctx.workflowNodeInstanceId() : null;
    }

    @Override
    public String getWorkflowInstanceId(String agentSessionId) {
        WorkflowContext ctx = agentSessionToWorkflow.get(agentSessionId);
        return ctx != null ? ctx.workflowInstanceId() : null;
    }

    @Override
    public String getWorkItemId(String agentSessionId) {
        WorkflowContext ctx = agentSessionToWorkflow.get(agentSessionId);
        return ctx != null ? ctx.workItemId() : null;
    }

    // --- Session lifecycle ---

    public void registerWorkflowContext(String agentSessionId, String workItemId,
                                         String workflowInstanceId, String workflowNodeInstanceId) {
        if (agentSessionId != null) {
            agentSessionToWorkflow.put(agentSessionId,
                new WorkflowContext(workItemId, workflowInstanceId, workflowNodeInstanceId));
        }
    }

    public void registerSession(String opencodeSessionId, String agentSessionId, String baseUrl, String workingDirectory) {
        opencodeToAgentSession.put(opencodeSessionId, agentSessionId);
        translationState.initSession(opencodeSessionId);

        if (subscriptionHandle == null) {
            startSubscription(baseUrl, workingDirectory);
        }
    }

    public void unregisterSession(String opencodeSessionId) {
        String agentSessionId = opencodeToAgentSession.remove(opencodeSessionId);
        translationState.cleanupSession(opencodeSessionId);
        if (agentSessionId != null) {
            projector.cleanupSession(agentSessionId);
            agentSessionToWorkflow.remove(agentSessionId);
        }
    }

    public void shutdown() {
        if (subscriptionHandle != null) {
            subscriptionHandle.close();
        }
        transport.shutdown();
    }

    // --- RuntimeEventSink implementation ---

    @Override
    public void onEvent(RuntimeRawEvent rawEvent) {
        String opencodeSessionId = rawEvent.runtimeSessionId();
        if (opencodeSessionId.isEmpty() || rawEvent.rawType().isEmpty()) return;

        String agentSessionId = opencodeToAgentSession.get(opencodeSessionId);
        if (agentSessionId == null) return;

        RuntimeRawEvent mapped = new RuntimeRawEvent(
            RuntimeType.OPENCODE, rawEvent.rawType(), rawEvent.rawJson(), opencodeSessionId);
        List<RuntimeEventEnvelope> envelopes = translator.translate(mapped, this);
        if (!envelopes.isEmpty()) {
            dispatcher.dispatch(envelopes);
        }
    }

    @Override
    public void onError(RuntimeTransportException error) {
        if (error.isRecoverable()) {
            log.warn("Recoverable SSE transport error: {}", error.getMessage());
        } else {
            log.error("Unrecoverable SSE transport error: {}", error.getMessage());
        }
        publishStreamDiagnostic(
            RuntimeEventTypes.RUNTIME_ERROR,
            "failed",
            "OpenCode 事件流异常",
            error.getMessage(),
            error.isRecoverable(),
            "event.stream.error"
        );
    }

    @Override
    public void onClose() {
        log.info("SSE event stream closed by transport");
        subscriptionHandle = null;
        publishStreamDiagnostic(
            RuntimeEventTypes.RUNTIME_STATUS_CHANGED,
            "disconnected",
            "OpenCode 事件流已断开",
            "OpenCode 事件流连接已关闭，后续事件需要重新订阅后才能继续同步。",
            true,
            "event.stream.closed"
        );
    }

    // --- Subscription setup ---

    private void startSubscription(String baseUrl, String workingDirectory) {
        transport.configure(baseUrl, workingDirectory);
        subscriptionHandle = transport.subscribe(this);
    }

    private void publishStreamDiagnostic(String eventType, String status, String title, String summary,
                                         boolean recoverable, String rawEventType) {
        List<RuntimeEventEnvelope> envelopes = new ArrayList<>();
        opencodeToAgentSession.forEach((opencodeSessionId, agentSessionId) -> {
            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put("type", "runtime_connection");
            payload.put("kind", "runtime_connection");
            payload.put("status", status);
            payload.put("title", title);
            payload.put("label", title);
            payload.put("summary", summary == null || summary.isBlank() ? title : summary);
            payload.put("recoverable", recoverable);
            payload.put("rawEventType", rawEventType);
            payload.put("runtimeSessionId", opencodeSessionId);

            WorkflowContext ctx = agentSessionToWorkflow.get(agentSessionId);
            envelopes.add(new RuntimeEventEnvelope(
                "runtime-event",
                eventType,
                null,
                null,
                null,
                RuntimeType.OPENCODE,
                agentSessionId,
                opencodeSessionId,
                ctx != null ? ctx.workItemId() : null,
                ctx != null ? ctx.workflowInstanceId() : null,
                ctx != null ? ctx.workflowNodeInstanceId() : null,
                payload,
                java.time.OffsetDateTime.now()
            ));
        });
        if (!envelopes.isEmpty()) {
            dispatcher.dispatch(envelopes);
        }
    }

    // --- Test support: expose normalizeAndPublish for glue tests ---

    void normalizeAndPublish(com.fasterxml.jackson.databind.JsonNode raw) {
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

    record WorkflowContext(String workItemId, String workflowInstanceId, String workflowNodeInstanceId) {}
}
