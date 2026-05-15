package com.agentcenter.bridge.infrastructure.runtime.aruntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.application.runtime.RuntimeCapabilities;
import com.agentcenter.bridge.application.runtime.RuntimeDescriptor;
import com.agentcenter.bridge.application.runtime.RuntimeInstructionInjectionMode;
import com.agentcenter.bridge.application.runtime.RuntimeOperationContext;
import com.agentcenter.bridge.application.runtime.RuntimeProvider;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * First A Runtime provider skeleton. It keeps enterprise protocol details behind RuntimeCommandTransport.
 */
public class ARuntimeProvider implements RuntimeProvider {

    private static final RuntimeCapabilities CAPABILITIES = new RuntimeCapabilities(
            true,
            true,
            false,
            true,
            RuntimeCapabilities.WEBSOCKET,
            RuntimeCapabilities.WEBSOCKET,
            RuntimeCapabilities.REMOTE_COMMAND,
            true,
            RuntimeInstructionInjectionMode.USER_PROMPT
    );

    private static final RuntimeDescriptor DESCRIPTOR = new RuntimeDescriptor(
            "A Runtime",
            "Provider-selected transport",
            "Enterprise A Runtime adapter through AgentCenter Java Bridge",
            CAPABILITIES
    );

    private final RuntimeCommandTransport commandTransport;
    private final ObjectMapper objectMapper;

    public ARuntimeProvider(RuntimeCommandTransport commandTransport, ObjectMapper objectMapper) {
        this.commandTransport = commandTransport;
        this.objectMapper = objectMapper;
    }

    @Override
    public RuntimeType runtimeType() {
        return RuntimeType.A_RUNTIME;
    }

    @Override
    public RuntimeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String createSession(String workItemId, String agentSessionId) {
        return createSessionWithContext(RuntimeOperationContext.forSession(workItemId, agentSessionId, null));
    }

    @Override
    public String createSessionWithContext(RuntimeOperationContext context) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", nonBlank(ctx.workItemId(), "AgentCenter Session"));

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE,
                RuntimeType.A_RUNTIME,
                ctx.runtimeSessionId(),
                payload,
                ctx);
        RuntimeAckEnvelope ack = commandTransport.send(command);
        ensureSuccess("create A Runtime session", ack);
        String runtimeSessionId = ack.payload() == null ? null : ack.payload().path("sessionId").asText(null);
        return nonBlank(runtimeSessionId, ctx.runtimeSessionId());
    }

    @Override
    public String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        return ensureSessionWithContext(RuntimeOperationContext.forSession(workItemId, agentSessionId, runtimeSessionId));
    }

    @Override
    public String ensureSessionWithContext(RuntimeOperationContext context) {
        return createSessionWithContext(context);
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        return runSkillWithContext(
                RuntimeOperationContext.empty().withRuntimeSessionId(sessionId),
                new SkillInvocationRequest(skillName, inputContext, null, RuntimeInstructionInjectionMode.USER_PROMPT));
    }

    @Override
    public SkillRunResult runSkillWithContext(RuntimeOperationContext context, SkillInvocationRequest request) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("skillName", request.skillName());
        payload.put("userPrompt", request.userPrompt());
        payload.put("instructionPrompt", request.instructionPrompt());

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SKILL_RUN,
                RuntimeType.A_RUNTIME,
                ctx.runtimeSessionId(),
                payload,
                ctx);
        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            return new SkillRunResult(false, null, "MARKDOWN", ack.message(), false);
        }
        String output = ack.payload() == null ? "" : ack.payload().path("output").asText("");
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        sendMessageWithContext(RuntimeOperationContext.empty().withRuntimeSessionId(sessionId), userMessage);
    }

    @Override
    public void sendMessageWithContext(RuntimeOperationContext context, String userMessage) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("text", userMessage);
        RuntimeAckEnvelope ack = commandTransport.send(RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND,
                RuntimeType.A_RUNTIME,
                ctx.runtimeSessionId(),
                payload,
                ctx));
        ensureSuccess("send A Runtime message", ack);
    }

    @Override
    public void cancel(String sessionId) {
        cancelWithContext(RuntimeOperationContext.empty().withRuntimeSessionId(sessionId));
    }

    @Override
    public void cancelWithContext(RuntimeOperationContext context) {
        RuntimeOperationContext ctx = context == null ? RuntimeOperationContext.empty() : context;
        RuntimeAckEnvelope ack = commandTransport.send(RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_CANCEL,
                RuntimeType.A_RUNTIME,
                ctx.runtimeSessionId(),
                objectMapper.createObjectNode(),
                ctx));
        ensureSuccess("cancel A Runtime conversation", ack);
    }

    @Override public void refreshSkills(RuntimeSkillSnapshot snapshot) {}
    @Override public void refreshMcps() {}
    @Override public List<RuntimeSkillDto> scanSkills() { return List.of(); }
    @Override public String installSkill(String skillName, Path sourceDir) { return ""; }
    @Override public void deleteSkillFiles(String relativePath, String skillName) {}
    @Override public String getSkillsRootPath() { return ""; }
    @Override public Map<String, Object> readMcpConfig() { return Map.of(); }
    @Override public void writeMcpConfig(Map<String, Object> config) {}

    private void ensureSuccess(String action, RuntimeAckEnvelope ack) {
        if (!ack.success()) {
            throw new IllegalStateException(action + " failed: " + ack.message());
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
