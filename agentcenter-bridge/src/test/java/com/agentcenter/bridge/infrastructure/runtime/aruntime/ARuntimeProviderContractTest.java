package com.agentcenter.bridge.infrastructure.runtime.aruntime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.runtime.RuntimeCapabilities;
import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;
import com.agentcenter.bridge.application.runtime.transport.RuntimeEventSink;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.application.runtime.translation.RuntimeTranslationContext;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;

class ARuntimeProviderContractTest {

    private ARuntimeFakeTransport transport;
    private ARuntimeProvider provider;
    private List<RuntimeRawEvent> rawEvents;

    @BeforeEach
    void setUp() {
        transport = new ARuntimeFakeTransport();
        provider = new ARuntimeProvider(transport, new ObjectMapper());
        rawEvents = new ArrayList<>();
        transport.subscribe(new RuntimeEventSink() {
            @Override public void onEvent(RuntimeRawEvent event) { rawEvents.add(event); }
            @Override public void onError(RuntimeTransportException error) {}
            @Override public void onClose() {}
        });
    }

    @Test
    void descriptorDeclaresARuntimeCapabilities() {
        assertThat(provider.runtimeType()).isEqualTo(RuntimeType.A_RUNTIME);
        assertThat(provider.descriptor().displayName()).isEqualTo("A Runtime");
        assertThat(provider.capabilities().commandTransport()).isEqualTo(RuntimeCapabilities.WEBSOCKET);
        assertThat(provider.capabilities().eventTransport()).isEqualTo(RuntimeCapabilities.WEBSOCKET);
        assertThat(provider.capabilities().supportsAsyncOperations()).isTrue();
    }

    @Test
    void createSessionAndSendMessageProduceAgentCenterEvents() {
        RuntimeOperationContext context = RuntimeOperationContext.empty()
                .withOperationId("op-1")
                .withAgentSessionId("agent-1")
                .withWorkItemId("work-1")
                .withWorkflowContext("wf-1", "node-1");

        String runtimeSessionId = provider.createSessionWithContext(context);
        provider.sendMessageWithContext(context.withRuntimeSessionId(runtimeSessionId), "hello");

        assertThat(runtimeSessionId).startsWith("arun_");
        assertThat(rawEvents).extracting(RuntimeRawEvent::rawType)
                .contains(
                        RuntimeEventTypes.RUNTIME_STATUS_CHANGED,
                        RuntimeEventTypes.CONVERSATION_DELTA,
                        RuntimeEventTypes.CONVERSATION_COMPLETED);
        RuntimeRawEvent delta = rawEvents.stream()
                .filter(event -> RuntimeEventTypes.CONVERSATION_DELTA.equals(event.rawType()))
                .findFirst()
                .orElseThrow();
        assertThat(delta.rawJson().path("operationId").asText()).isEqualTo("op-1");
        assertThat(delta.rawJson().path("agentSessionId").asText()).isEqualTo("agent-1");
        assertThat(delta.rawJson().path("workflowNodeInstanceId").asText()).isEqualTo("node-1");
    }

    @Test
    void runSkillReturnsOutputFromAckPayload() {
        RuntimeOperationContext context = RuntimeOperationContext.empty()
                .withRuntimeSessionId("arun_existing")
                .withAgentSessionId("agent-1");

        var result = provider.runSkillWithContext(
                context,
                SkillInvocationRequest.userPromptInjection("prd-design", "input", "instruction"));

        assertThat(result.success()).isTrue();
        assertThat(result.outputContent()).contains("prd-design");
        assertThat(rawEvents).extracting(RuntimeRawEvent::rawType)
                .contains(RuntimeEventTypes.SKILL_RUN_STARTED, RuntimeEventTypes.SKILL_RUN_COMPLETED);
    }

    @Test
    void translatorMapsRawEventToAgentCenterEnvelope() {
        RuntimeOperationContext context = RuntimeOperationContext.empty()
                .withOperationId("op-1")
                .withAgentSessionId("agent-1")
                .withRuntimeSessionId("arun_1")
                .withWorkItemId("work-1")
                .withWorkflowContext("wf-1", "node-1");
        provider.sendMessageWithContext(context, "hello");

        RuntimeRawEvent raw = rawEvents.stream()
                .filter(event -> RuntimeEventTypes.CONVERSATION_DELTA.equals(event.rawType()))
                .findFirst()
                .orElseThrow();

        var envelopes = new ARuntimeEventTranslator().translate(raw, new StubTranslationContext());

        assertThat(envelopes).hasSize(1);
        var envelope = envelopes.get(0);
        assertThat(envelope.type()).isEqualTo(RuntimeEventTypes.CONVERSATION_DELTA);
        assertThat(envelope.runtimeType()).isEqualTo(RuntimeType.A_RUNTIME);
        assertThat(envelope.agentSessionId()).isEqualTo("agent-1");
        assertThat(envelope.runtimeSessionId()).isEqualTo("arun_1");
        assertThat(envelope.operationId()).isEqualTo("op-1");
        assertThat(envelope.workItemId()).isEqualTo("work-1");
        assertThat(envelope.workflowInstanceId()).isEqualTo("wf-1");
        assertThat(envelope.workflowNodeInstanceId()).isEqualTo("node-1");
    }

    private static class StubTranslationContext implements RuntimeTranslationContext {
        @Override public String getAgentSessionId(String runtimeSessionId) { return "agent-fallback"; }
        @Override public boolean isUserMessage(String runtimeSessionId, String messageId) { return false; }
        @Override public void recordUserMessageId(String runtimeSessionId, String messageId) {}
        @Override public String getWorkflowNodeInstanceId(String agentSessionId) { return "node-fallback"; }
        @Override public String getWorkflowInstanceId(String agentSessionId) { return "wf-fallback"; }
        @Override public String getWorkItemId(String agentSessionId) { return "work-fallback"; }
    }
}
