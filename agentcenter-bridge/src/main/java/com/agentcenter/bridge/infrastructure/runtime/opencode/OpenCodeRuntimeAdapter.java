package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeHttpCommandTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;

/**
 * Connects to {@code opencode serve} via HTTP API to create sessions,
 * send messages via {@code prompt_async}, and consume SSE events.
 *
 * <p>Runtime session IDs persisted in AgentCenter are real OpenCode session IDs.
 * An in-memory agentSessionId → opencodeSessionId map is restored lazily when needed.</p>
 *
 * <p>Event flow: opencode SSE → {@link OpenCodeEventSubscriber} →
 * {@link com.agentcenter.bridge.application.RuntimeEventService} →
 * Java SSE to frontend.</p>
 */
@Component
public class OpenCodeRuntimeAdapter implements AgentRuntimeAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeRuntimeAdapter.class);
    private final OpenCodeProcessManager processManager;
    private final OpenCodeEventSubscriber eventSubscriber;
    private final ObjectMapper objectMapper;
    private final OpenCodeSkillFileService skillFileService;
    private final OpenCodeMcpFileService mcpFileService;
    private final OpenCodeHttpCommandTransport commandTransport;
    private final String agent;
    private final int responseTimeoutSeconds;

    private final Map<String, String> agentToOpencodeSession = new ConcurrentHashMap<>();

    public OpenCodeRuntimeAdapter(
            OpenCodeProcessManager processManager,
            OpenCodeEventSubscriber eventSubscriber,
            ObjectMapper objectMapper,
            OpenCodeSkillFileService skillFileService,
            OpenCodeMcpFileService mcpFileService,
            OpenCodeHttpCommandTransport commandTransport,
            @Value("${agentcenter.runtime.opencode.serve.agent:build}") String agent,
            @Value("${agentcenter.runtime.opencode.timeout-seconds:180}") int responseTimeoutSeconds) {
        this.processManager = processManager;
        this.eventSubscriber = eventSubscriber;
        this.objectMapper = objectMapper;
        this.skillFileService = skillFileService;
        this.mcpFileService = mcpFileService;
        this.commandTransport = commandTransport;
        this.agent = agent;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    @Override
    public List<RuntimeSkillDto> scanSkills() {
        return skillFileService.scanSkills();
    }

    @Override
    public String installSkill(String skillName, Path sourceDir) {
        return skillFileService.installSkill(skillName, sourceDir);
    }

    @Override
    public void deleteSkillFiles(String relativePath, String skillName) {
        skillFileService.deleteSkillFiles(relativePath, skillName);
    }

    @Override
    public String getSkillsRootPath() {
        return skillFileService.getSkillsRootPath();
    }

    @Override
    public String createSession(String workItemId, String agentSessionId) {
        if (!processManager.isEnabled()) {
            throw new IllegalStateException("OpenCode serve adapter is disabled");
        }

        String baseUrl = processManager.ensureRunning();

        String title = "AgentCenter Session";
        if (workItemId != null && !workItemId.isBlank()) {
            title = "AgentCenter · " + workItemId;
        }

        ObjectNode sessionPayload = objectMapper.createObjectNode();
        sessionPayload.put("baseUrl", baseUrl);
        sessionPayload.put("workingDirectory", processManager.resolveWorkingDirectory().toString());
        sessionPayload.put("title", title);
        ArrayNode permissions = sessionPayload.putArray("permission");
        ObjectNode perm = permissions.addObject();
        perm.put("permission", "edit");
        perm.put("pattern", "*");
        perm.put("action", "ask");

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.SESSION_ENSURE, RuntimeType.OPENCODE, null, sessionPayload);

        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            throw new RuntimeException("Failed to create opencode session: " + ack.message());
        }

        String opencodeSessionId = ack.payload().path("sessionId").asText("");
        if (opencodeSessionId.isEmpty()) {
            throw new RuntimeException("opencode session ack missing sessionId: " + ack.payload());
        }

        String agentSid = (agentSessionId != null && !agentSessionId.isBlank())
                ? agentSessionId
                : (workItemId != null ? "acs_" + workItemId : "acs_" + System.currentTimeMillis());
        agentToOpencodeSession.put(agentSid, opencodeSessionId);
        agentToOpencodeSession.put(opencodeSessionId, opencodeSessionId);

        String cwd = processManager.resolveWorkingDirectory().toString();
        eventSubscriber.registerSession(opencodeSessionId, agentSid, baseUrl, cwd);

        log.info("Created opencode session {} → agent session {}", opencodeSessionId, agentSid);
        return opencodeSessionId;
    }

    @Override
    public String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        if (runtimeSessionId == null || runtimeSessionId.isBlank() || !runtimeSessionId.startsWith("ses_")) {
            return createSession(workItemId, agentSessionId);
        }

        String baseUrl = processManager.ensureRunning();
        String cwd = processManager.resolveWorkingDirectory().toString();
        if (commandTransport.fetchMessages(baseUrl, cwd, runtimeSessionId) == null) {
            log.info("Persisted opencode session {} is not available; creating a replacement for agent session {}",
                    runtimeSessionId, agentSessionId);
            return createSession(workItemId, agentSessionId);
        }

        agentToOpencodeSession.put(agentSessionId, runtimeSessionId);
        agentToOpencodeSession.put(runtimeSessionId, runtimeSessionId);
        eventSubscriber.registerSession(runtimeSessionId, agentSessionId, baseUrl, cwd);
        return runtimeSessionId;
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        String output = dispatchPromptAndWait(
                sessionId,
                buildSkillPrompt(skillName, inputContext),
                true);
        if (output == null || output.isBlank()) {
            output = """
                    # Skill 已分发到 OpenCode

                    - Skill: `%s`
                    - 会话: `%s`

                    OpenCode 已接收任务，但在等待窗口内没有返回可展示文本。
                    """.formatted(skillName, sessionId).trim();
        }
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        dispatchPrompt(sessionId, userMessage);
    }

    private String buildSkillPrompt(String skillName, String inputContext) {
        return """
                你是 AgentCenter 后台工作流节点执行器。请使用 OpenCode skill `%s` 的规范处理下面的输入。

                约束：
                - 只返回本节点最终 Markdown 文档。
                - 不修改文件，不生成补丁，不主动遍历仓库，除非输入上下文明确要求。
                - 如果信息不足，请在 Markdown 中列出"缺口与待确认项"，不要长时间探索。
                - 输出使用中文，结构清晰，适合直接作为本节点产物进入下一节点。

                输入上下文：

                ```text
                %s
                ```
                """.formatted(skillName, inputContext).trim();
    }

    private String dispatchPromptAndWait(String sessionId, String userMessage, boolean allowToolOutputFallback) {
        DispatchContext context = dispatchPrompt(sessionId, userMessage);
        return waitForAssistantText(
                context.baseUrl(),
                context.cwd(),
                context.opencodeSessionId(),
                context.knownMessageIds(),
                allowToolOutputFallback);
    }

    private DispatchContext dispatchPrompt(String sessionId, String userMessage) {
        String opencodeSessionId = agentToOpencodeSession.get(sessionId);
        if (opencodeSessionId == null && sessionId != null && sessionId.startsWith("ses_")) {
            opencodeSessionId = sessionId;
            agentToOpencodeSession.put(sessionId, sessionId);
        }
        if (opencodeSessionId == null) {
            throw new IllegalArgumentException("No opencode session mapped for agent session: " + sessionId);
        }

        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();
        Set<String> knownMessageIds = fetchMessageIds(baseUrl, cwd.toString(), opencodeSessionId);

        ObjectNode sendPayload = objectMapper.createObjectNode();
        sendPayload.put("baseUrl", baseUrl);
        sendPayload.put("workingDirectory", cwd.toString());
        sendPayload.put("agent", agent);
        ArrayNode parts = sendPayload.putArray("parts");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", userMessage);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RuntimeType.OPENCODE,
                opencodeSessionId, sendPayload);

        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            throw new RuntimeException("opencode prompt_async failed: " + ack.message());
        }

        log.debug("Sent message to opencode session {} (agent session {})", opencodeSessionId, sessionId);
        return new DispatchContext(baseUrl, cwd.toString(), opencodeSessionId, knownMessageIds);
    }

    @Override
    public void cancel(String sessionId) {
        String opencodeSessionId = agentToOpencodeSession.remove(sessionId);
        if (opencodeSessionId != null) {
            eventSubscriber.unregisterSession(opencodeSessionId);
            log.info("Cancelled opencode session {} (agent session {})", opencodeSessionId, sessionId);
        }
    }

    @Override
    public void refreshMcps() {
        agentToOpencodeSession.clear();
        processManager.restartIfRunning();
    }

    @Override
    public Map<String, Object> readMcpConfig() {
        return mcpFileService.readMcpConfig();
    }

    @Override
    public void writeMcpConfig(Map<String, Object> config) {
        mcpFileService.writeMcpConfig(config);
    }

    @PreDestroy
    public void destroy() {
        eventSubscriber.shutdown();
        processManager.shutdown();
    }

    public String getOpencodeSessionId(String agentSessionId) {
        return agentToOpencodeSession.get(agentSessionId);
    }

    private Set<String> fetchMessageIds(String baseUrl, String cwd, String opencodeSessionId) {
        Set<String> ids = new HashSet<>();
        JsonNode messages = commandTransport.fetchMessages(baseUrl, cwd, opencodeSessionId);
        if (messages != null && messages.isArray()) {
            for (JsonNode message : messages) {
                String id = message.path("info").path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private String waitForAssistantText(String baseUrl, String cwd,
                                        String opencodeSessionId,
                                        Set<String> knownMessageIds,
                                        boolean allowToolOutputFallback) {
        long deadline = System.nanoTime() + Duration.ofSeconds(responseTimeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode messages = commandTransport.fetchMessages(baseUrl, cwd, opencodeSessionId);
            String text = extractNewAssistantText(messages, knownMessageIds);
            if (text != null && !text.isBlank()) {
                return text;
            }
            if (allowToolOutputFallback) {
                String toolOutput = extractNewCompletedToolOutput(messages, knownMessageIds);
                if (toolOutput != null && !toolOutput.isBlank()) {
                    return toolOutput;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private String extractNewAssistantText(JsonNode messages, Set<String> knownMessageIds) {
        if (messages == null || !messages.isArray()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (JsonNode message : messages) {
            JsonNode info = message.path("info");
            String id = info.path("id").asText("");
            if (id.isBlank() || knownMessageIds.contains(id)) {
                continue;
            }
            if (!"assistant".equals(info.path("role").asText(""))) {
                continue;
            }

            String finish = info.path("finish").asText("");
            if (finish.isBlank()) {
                continue;
            }
            if ("tool-calls".equals(finish)) {
                continue;
            }

            for (JsonNode part : message.path("parts")) {
                if ("text".equals(part.path("type").asText(""))) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        result.append(text).append("\n\n");
                    }
                }
            }
        }
        return result.toString().trim();
    }

    private String extractNewCompletedToolOutput(JsonNode messages, Set<String> knownMessageIds) {
        if (messages == null || !messages.isArray()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (JsonNode message : messages) {
            JsonNode info = message.path("info");
            String id = info.path("id").asText("");
            if (id.isBlank() || knownMessageIds.contains(id)) {
                continue;
            }
            if (!"assistant".equals(info.path("role").asText(""))) {
                continue;
            }

            for (JsonNode part : message.path("parts")) {
                if (!"tool".equals(part.path("type").asText(""))) {
                    continue;
                }
                JsonNode state = part.path("state");
                if (!"completed".equals(state.path("status").asText(""))) {
                    continue;
                }
                JsonNode outputNode = state.path("output");
                if (outputNode.isMissingNode() || outputNode.isNull()) {
                    outputNode = state.path("result");
                }
                String output = stringifyValue(outputNode);
                if (!output.isBlank()) {
                    result.append(output).append("\n\n");
                }
            }
        }
        return result.toString().trim();
    }

    private String stringifyValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private record DispatchContext(String baseUrl, String cwd,
                                   String opencodeSessionId,
                                   Set<String> knownMessageIds) {}
}
