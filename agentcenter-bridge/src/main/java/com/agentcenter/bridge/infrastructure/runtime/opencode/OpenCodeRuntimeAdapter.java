package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.api.dto.RuntimeSkillDto;
import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.application.runtime.RuntimeSkillSnapshot;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
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
    private static final String RUNTIME_WORKSPACE_BOUNDARY = """
            运行边界：
            - 当前工作目录是 AgentCenter 为 Runtime 准备的隔离工作区；只读取、搜索和修改该工作目录内的文件。
            - 不要访问、搜索或引用 AgentCenter 源码目录、父目录、用户主目录或其他绝对路径，除非输入上下文明确把某个文件内容作为资料提供。
            - 如果任务需要工作目录之外的信息，请先说明缺失信息，并向 AgentCenter 请求补充，不要自行扩大读取范围。""";
    private static final String CONVERSATION_ARTIFACT_CAPTURE_INSTRUCTION = """

            AgentCenter 产物保存规则：
            - 普通问答、解释、排查过程不要输出产物协议。
            - 当用户明确要求生成 PRD、设计文档、报告、方案、补丁说明等可沉淀交付物时，请把最终交付物包在以下隐藏协议中，系统会保存为可预览产物：
            <!-- AGENTCENTER_ARTIFACT_BEGIN
            title: 简短产物标题.md
            type: MARKDOWN
            -->
            这里放最终产物正文。
            <!-- AGENTCENTER_ARTIFACT_END -->""";
    private final OpenCodeProcessManager processManager;
    private final OpenCodeEventSubscriber eventSubscriber;
    private final ObjectMapper objectMapper;
    private final OpenCodeSkillFileService skillFileService;
    private final OpenCodeMcpFileService mcpFileService;
    private final OpenCodeHttpCommandTransport commandTransport;
    private final RuntimeEventService runtimeEventService;
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
            RuntimeEventService runtimeEventService,
            @Value("${agentcenter.runtime.opencode.serve.agent:build}") String agent,
            @Value("${agentcenter.runtime.opencode.timeout-seconds:180}") int responseTimeoutSeconds) {
        this.processManager = processManager;
        this.eventSubscriber = eventSubscriber;
        this.objectMapper = objectMapper;
        this.skillFileService = skillFileService;
        this.mcpFileService = mcpFileService;
        this.commandTransport = commandTransport;
        this.runtimeEventService = runtimeEventService;
        this.agent = agent;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    @Override
    public List<RuntimeSkillDto> scanSkills() {
        return skillFileService.scanSkills();
    }

    public List<RuntimeSkillDto> scanSkills(Path projectWorkdir) {
        return skillFileService.scanSkills(projectWorkdir);
    }

    @Override
    public String installSkill(String skillName, Path sourceDir) {
        return skillFileService.installSkill(skillName, sourceDir);
    }

    public String installSkill(Path projectWorkdir, String skillName, Path sourceDir) {
        return skillFileService.installSkill(projectWorkdir, skillName, sourceDir);
    }

    @Override
    public void deleteSkillFiles(String relativePath, String skillName) {
        skillFileService.deleteSkillFiles(relativePath, skillName);
    }

    public void deleteSkillFiles(Path projectWorkdir, String relativePath, String skillName) {
        skillFileService.deleteSkillFiles(projectWorkdir, relativePath, skillName);
    }

    @Override
    public String getSkillsRootPath() {
        return skillFileService.getSkillsRootPath();
    }

    public String getSkillsRootPath(Path projectWorkdir) {
        return skillFileService.getSkillsRootPath(projectWorkdir);
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
                false);
        if (output == null || output.isBlank()) {
            return new SkillRunResult(
                    false,
                    null,
                    "MARKDOWN",
                    "Agent Runtime 已接收 Skill `" + skillName + "`，但在 "
                            + responseTimeoutSeconds + " 秒内没有返回可用输出。",
                    false
            );
        }
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    /**
     * Sends a skill invocation as 3 separate text parts via prompt_async.
     * This avoids wrapping the instructionPrompt (node state protocol) inside a code block.
     */
    public SkillRunResult runSkill(String sessionId, SkillInvocationRequest request) {
        ArrayNode parts = buildSkillParts(request);
        String output = dispatchMultiPartPromptAndWait(sessionId, parts, request.skillName());
        if (output == null || output.isBlank()) {
            return new SkillRunResult(
                    false,
                    null,
                    "MARKDOWN",
                    "Agent Runtime 已接收 Skill `" + request.skillName() + "`，但在 "
                            + responseTimeoutSeconds + " 秒内没有返回可用输出。",
                    false
            );
        }
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    @Override
    public void sendMessage(String sessionId, String userMessage) {
        dispatchPrompt(sessionId, userMessage, true);
    }

    private String buildSkillPrompt(String skillName, String inputContext) {
        return """
                请使用当前 Agent Runtime 中的 Skill `%s` 处理下面的用户输入。

                工作方式：
                - 优先遵循 Skill 自身说明和当前会话上下文。
                - AgentCenter 工作流只提供调用顺序、工作项信息、上游产物和用户交互回答，不替代 Skill 的判断。
                - 如果需要用户继续澄清、选择、确认或授权，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。
                - 如果当前 Runtime 不能使用 Question，再在输出末尾按 AgentCenter 节点状态协议声明 NEEDS_USER_INPUT。
                - 如果信息已经足够，请输出当前 Skill 的最终结果。

                %s

                输入上下文：

                ```text
                %s
                ```
                """.formatted(skillName, RUNTIME_WORKSPACE_BOUNDARY, inputContext).trim();
    }

    /**
     * Builds 3 text parts for the prompt_async API:
     * <ol>
     *   <li>Skill invocation instruction with workflow guidance</li>
     *   <li>User input context wrapped in a text code block</li>
     *   <li>AgentCenter node state protocol as plain markdown (no code block wrapping)</li>
     * </ol>
     * Part 3 is omitted when instructionPrompt is null or blank.
     */
    ArrayNode buildSkillParts(SkillInvocationRequest request) {
        ArrayNode parts = objectMapper.createArrayNode();

        // Part 1: Skill invocation instruction
        String part1Text = """
                请使用当前 Agent Runtime 中的 Skill `%s` 处理下面的用户输入。

                工作方式：
                - 优先遵循 Skill 自身说明和当前会话上下文。
                - AgentCenter 工作流只提供调用顺序、工作项信息、上游产物和用户交互回答，不替代 Skill 的判断。
                - 如果需要用户继续澄清、选择、确认或授权，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。
                - 如果当前 Runtime 不能使用 Question，再在输出末尾按 AgentCenter 节点状态协议声明 NEEDS_USER_INPUT。
                - 如果信息已经足够，请输出当前 Skill 的最终结果。

                %s""".formatted(request.skillName(), RUNTIME_WORKSPACE_BOUNDARY).trim();

        ObjectNode part1 = parts.addObject();
        part1.put("type", "text");
        part1.put("text", part1Text);

        // Part 2: User input context in text code block
        String part2Text = "输入上下文：\n\n```text\n" + request.userPrompt() + "\n```";
        ObjectNode part2 = parts.addObject();
        part2.put("type", "text");
        part2.put("text", part2Text);

        // Part 3: AgentCenter node state protocol — plain markdown, no code block
        if (request.instructionPrompt() != null && !request.instructionPrompt().isBlank()) {
            ObjectNode part3 = parts.addObject();
            part3.put("type", "text");
            part3.put("text", request.instructionPrompt());
        }

        return parts;
    }

    private String dispatchPromptAndWait(String sessionId, String userMessage, boolean allowToolOutputFallback) {
        DispatchContext context = dispatchPrompt(sessionId, userMessage, false);
        return waitForAssistantText(
                context.baseUrl(),
                context.cwd(),
                context.opencodeSessionId(),
                context.knownMessageIds(),
                allowToolOutputFallback);
    }

    private String dispatchMultiPartPromptAndWait(String sessionId, ArrayNode parts, String skillName) {
        DispatchContext context = dispatchMultiPartPrompt(sessionId, parts, skillName);
        return waitForAssistantText(
                context.baseUrl(),
                context.cwd(),
                context.opencodeSessionId(),
                context.knownMessageIds(),
                false);
    }

    private DispatchContext dispatchPrompt(String sessionId, String userMessage, boolean includeArtifactInstruction) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent session id is required before dispatching to opencode");
        }
        String opencodeSessionId = agentToOpencodeSession.get(sessionId);
        if (opencodeSessionId == null && sessionId.startsWith("ses_")) {
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
        textPart.put("text", includeArtifactInstruction ? withConversationArtifactInstruction(userMessage) : userMessage);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RuntimeType.OPENCODE,
                opencodeSessionId, sendPayload);

        publishPromptDebugEvent(sessionId, opencodeSessionId, baseUrl, cwd.toString(), sendPayload, userMessage);

        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            throw new RuntimeException("opencode prompt_async failed: " + ack.message());
        }

        log.debug("Sent message to opencode session {} (agent session {})", opencodeSessionId, sessionId);
        return new DispatchContext(baseUrl, cwd.toString(), opencodeSessionId, knownMessageIds);
    }

    private String withConversationArtifactInstruction(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return CONVERSATION_ARTIFACT_CAPTURE_INSTRUCTION.trim();
        }
        return userMessage + "\n\n" + CONVERSATION_ARTIFACT_CAPTURE_INSTRUCTION.trim();
    }

    private DispatchContext dispatchMultiPartPrompt(String sessionId, ArrayNode parts, String skillName) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent session id is required before dispatching skill " + skillName);
        }
        String opencodeSessionId = agentToOpencodeSession.get(sessionId);
        if (opencodeSessionId == null && sessionId.startsWith("ses_")) {
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
        sendPayload.set("parts", parts);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND, RuntimeType.OPENCODE,
                opencodeSessionId, sendPayload);

        String userPromptSummary = "[multi-part skill invocation: " + skillName + "]";
        publishPromptDebugEvent(sessionId, opencodeSessionId, baseUrl, cwd.toString(), sendPayload, userPromptSummary);

        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            throw new RuntimeException("opencode prompt_async failed: " + ack.message());
        }

        log.debug("Sent multi-part skill prompt to opencode session {} (agent session {})", opencodeSessionId, sessionId);
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
    public void refreshSkills(RuntimeSkillSnapshot snapshot) {
        agentToOpencodeSession.clear();
        processManager.restartIfRunning();
    }

    public void refreshMcps(Path projectWorkdir) {
        refreshMcps();
    }

    @Override
    public Map<String, Object> readMcpConfig() {
        return mcpFileService.readMcpConfig();
    }

    public Map<String, Object> readMcpConfig(Path projectWorkdir) {
        return mcpFileService.readMcpConfig(projectWorkdir);
    }

    @Override
    public void writeMcpConfig(Map<String, Object> config) {
        mcpFileService.writeMcpConfig(config);
    }

    public void writeMcpConfig(Path projectWorkdir, Map<String, Object> config) {
        mcpFileService.writeMcpConfig(projectWorkdir, config);
    }

    @Override
    public void registerWorkflowNodeContext(String agentSessionId, String workItemId,
                                              String workflowInstanceId, String workflowNodeInstanceId) {
        eventSubscriber.registerWorkflowContext(agentSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId);
    }

    @PreDestroy
    public void destroy() {
        eventSubscriber.shutdown();
        processManager.shutdown();
    }

    public String getOpencodeSessionId(String agentSessionId) {
        return agentToOpencodeSession.get(agentSessionId);
    }

    public void respondPermission(String opencodeSessionId, String permissionId, boolean approved) {
        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd.toString());
        payload.put("permissionId", permissionId);
        payload.put("approved", approved);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.PERMISSION_RESPOND, RuntimeType.OPENCODE,
            opencodeSessionId, payload);

        commandTransport.send(command);
    }

    public void replyQuestion(String opencodeSessionId, String requestId, List<List<String>> answers) {
        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd.toString());
        payload.put("requestId", requestId);
        ArrayNode answerGroups = payload.putArray("answers");
        for (List<String> answerGroup : answers) {
            ArrayNode group = answerGroups.addArray();
            for (String answer : answerGroup) {
                group.add(answer);
            }
        }

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.QUESTION_REPLY, RuntimeType.OPENCODE,
            opencodeSessionId, payload);

        commandTransport.send(command);
    }

    public void rejectQuestion(String opencodeSessionId, String requestId) {
        String baseUrl = processManager.ensureRunning();
        Path cwd = processManager.resolveWorkingDirectory();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd.toString());
        payload.put("requestId", requestId);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.QUESTION_REJECT, RuntimeType.OPENCODE,
            opencodeSessionId, payload);

        commandTransport.send(command);
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

    private void publishPromptDebugEvent(String dispatchSessionId,
                                         String opencodeSessionId,
                                         String baseUrl,
                                         String cwd,
                                         ObjectNode requestPayload,
                                         String userPrompt) {
        try {
            String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
            if ((agentSessionId == null || agentSessionId.isBlank())
                    && dispatchSessionId != null
                    && !dispatchSessionId.startsWith("ses_")) {
                agentSessionId = dispatchSessionId;
            }
            if (agentSessionId == null || agentSessionId.isBlank()) {
                return;
            }

            ObjectNode opencodePromptAsyncBody = objectMapper.createObjectNode();
            opencodePromptAsyncBody.put("agent", agent);
            opencodePromptAsyncBody.set("parts", requestPayload.path("parts").deepCopy());

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("kind", "prompt_debug");
            payload.put("status", "debug");
            payload.put("title", "Prompt Debug");
            payload.put("summary", "本轮发送给 OpenCode Runtime 的 prompt_async 请求");
            payload.put("agent", agent);
            payload.put("runtimeSessionId", opencodeSessionId);
            payload.put("baseUrl", baseUrl);
            payload.put("workingDirectory", cwd);
            payload.put("systemPrompt", "AgentCenter 当前没有向 OpenCode prompt_async 发送显式 system prompt；"
                    + "本轮用户消息内包含 Runtime 工作目录边界约束；系统/agent 指令由 OpenCode agent=`"
                    + agent + "`、OpenCode 配置与 Skill 文件加载。");
            payload.put("userPrompt", userPrompt);
            payload.set("requestPayload", requestPayload.deepCopy());
            payload.set("opencodePromptAsyncBody", opencodePromptAsyncBody);

            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    RuntimeEventType.PROCESS_TRACE,
                    RuntimeEventSource.OPENCODE,
                    payload.toString(),
                    null
            ));
        } catch (Exception e) {
            log.debug("Failed to publish prompt debug event for opencode session {}", opencodeSessionId, e);
        }
    }

    private String waitForAssistantText(String baseUrl, String cwd,
                                        String opencodeSessionId,
                                        Set<String> knownMessageIds,
                                        boolean allowToolOutputFallback) {
        long deadline = System.nanoTime() + Duration.ofSeconds(responseTimeoutSeconds).toNanos();
        Map<String, Integer> streamedTextLengths = new HashMap<>();
        while (System.nanoTime() < deadline) {
            JsonNode messages = commandTransport.fetchMessages(baseUrl, cwd, opencodeSessionId);
            publishAssistantTextSnapshotDeltas(opencodeSessionId, messages, knownMessageIds, streamedTextLengths);
            String text = extractNewAssistantText(messages, knownMessageIds);
            if (text != null && !text.isBlank()) {
                publishAssistantCompleted(opencodeSessionId);
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
        publishAssistantTimeout(opencodeSessionId);
        return null;
    }

    private void publishAssistantTimeout(String opencodeSessionId) {
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        String message = "Agent Runtime 已接收请求，但在 " + responseTimeoutSeconds + " 秒内没有返回可用输出。";
        try {
            ObjectNode errorPayload = objectMapper.createObjectNode();
            errorPayload.put("kind", "error");
            errorPayload.put("status", "failed");
            errorPayload.put("title", "Runtime 响应超时");
            errorPayload.put("summary", message);
            errorPayload.put("reason", "RUNTIME_TIMEOUT");
            errorPayload.put("errorCode", "RUNTIME_TIMEOUT");
            errorPayload.put("errorMessage", message);
            errorPayload.put("recoverable", true);
            errorPayload.put("rawEventType", "session.messages.timeout");
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    RuntimeEventType.ERROR,
                    RuntimeEventSource.OPENCODE,
                    errorPayload.toString(),
                    null
            ));

            ObjectNode tracePayload = errorPayload.deepCopy();
            tracePayload.put("visibility", "public_summary");
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    RuntimeEventType.PROCESS_TRACE,
                    RuntimeEventSource.OPENCODE,
                    tracePayload.toString(),
                    null
            ));
        } catch (Exception e) {
            log.debug("Failed to publish timeout event for opencode session {}", opencodeSessionId, e);
        }
    }

    private void publishAssistantTextSnapshotDeltas(String opencodeSessionId,
                                                    JsonNode messages,
                                                    Set<String> knownMessageIds,
                                                    Map<String, Integer> streamedTextLengths) {
        if (messages == null || !messages.isArray()) {
            return;
        }
        for (JsonNode message : messages) {
            JsonNode info = message.path("info");
            String id = info.path("id").asText("");
            if (id.isBlank() || knownMessageIds.contains(id)) {
                continue;
            }
            if (!"assistant".equals(info.path("role").asText(""))) {
                continue;
            }
            if ("tool-calls".equals(info.path("finish").asText(""))) {
                continue;
            }

            String text = collectTextParts(message);
            int previousLength = streamedTextLengths.getOrDefault(id, 0);
            if (text.length() <= previousLength) {
                continue;
            }

            String delta = text.substring(previousLength);
            streamedTextLengths.put(id, text.length());
            publishAssistantDelta(opencodeSessionId, id, delta);
        }
    }

    private String collectTextParts(JsonNode message) {
        StringBuilder result = new StringBuilder();
        for (JsonNode part : message.path("parts")) {
            if (!"text".equals(part.path("type").asText(""))) {
                continue;
            }
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (!result.isEmpty()) {
                    result.append("\n\n");
                }
                result.append(text);
            }
        }
        return result.toString();
    }

    private void publishAssistantDelta(String opencodeSessionId, String messageId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("delta", delta);
            payload.put("messageId", messageId);
            payload.put("rawEventType", "session.messages.snapshot");
            payload.put("rawPartType", "text");
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    RuntimeEventType.ASSISTANT_DELTA,
                    RuntimeEventSource.OPENCODE,
                    payload.toString(),
                    null
            ));
        } catch (Exception e) {
            log.debug("Failed to publish assistant delta for opencode session {}", opencodeSessionId, e);
        }
    }

    private void publishAssistantCompleted(String opencodeSessionId) {
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("rawEventType", "session.messages.snapshot");
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    RuntimeEventType.ASSISTANT_COMPLETED,
                    RuntimeEventSource.OPENCODE,
                    payload.toString(),
                    null
            ));
        } catch (Exception e) {
            log.debug("Failed to publish assistant completed for opencode session {}", opencodeSessionId, e);
        }
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
