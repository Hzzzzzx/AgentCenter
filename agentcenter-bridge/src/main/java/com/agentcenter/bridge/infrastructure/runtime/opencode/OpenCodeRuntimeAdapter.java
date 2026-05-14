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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.ProjectRuntimeWorkspaceResolver;
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
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
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
    private static final int LOG_FIELD_LIMIT = 180;
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
    private static final String USER_CANCELLED_NODE_STATE = """
            <!-- AGENTCENTER_NODE_STATE
            status: IN_PROGRESS
            reason: 用户已暂停当前回复，可补充输入后继续。
            -->
            """.trim();
    private final OpenCodeProcessManager processManager;
    private final OpenCodeEventSubscriber eventSubscriber;
    private final ObjectMapper objectMapper;
    private final OpenCodeSkillFileService skillFileService;
    private final OpenCodeMcpFileService mcpFileService;
    private final OpenCodeHttpCommandTransport commandTransport;
    private final RuntimeEventService runtimeEventService;
    private final WorkItemMapper workItemMapper;
    private final ProjectRuntimeWorkspaceResolver workspaceResolver;
    private final String agent;
    private final int responseTimeoutSeconds;
    private final int waitInterruptedRetryLimit;
    private final long waitInterruptedRetryBackoffMs;
    private final int pollErrorRetryLimit;
    private final long pollErrorRetryBackoffMs;

    @Value("${agentcenter.trace.conversation-log-enabled:false}")
    private boolean conversationLogEnabled;

    private final Map<String, String> agentToOpencodeSession = new ConcurrentHashMap<>();
    private final Map<String, String> sessionWorkingDirectories = new ConcurrentHashMap<>();
    private final Map<String, Long> cancelGenerations = new ConcurrentHashMap<>();

    @Autowired
    public OpenCodeRuntimeAdapter(
            OpenCodeProcessManager processManager,
            OpenCodeEventSubscriber eventSubscriber,
            ObjectMapper objectMapper,
            OpenCodeSkillFileService skillFileService,
            OpenCodeMcpFileService mcpFileService,
            OpenCodeHttpCommandTransport commandTransport,
            RuntimeEventService runtimeEventService,
            WorkItemMapper workItemMapper,
            ProjectRuntimeWorkspaceResolver workspaceResolver,
            @Value("${agentcenter.runtime.opencode.serve.agent:build}") String agent,
            @Value("${agentcenter.runtime.opencode.timeout-seconds:180}") int responseTimeoutSeconds,
            @Value("${agentcenter.runtime.opencode.wait-interrupted-retry-limit:1}") int waitInterruptedRetryLimit,
            @Value("${agentcenter.runtime.opencode.wait-interrupted-retry-backoff-ms:700}") long waitInterruptedRetryBackoffMs,
            @Value("${agentcenter.runtime.opencode.poll-error-retry-limit:3}") int pollErrorRetryLimit,
            @Value("${agentcenter.runtime.opencode.poll-error-retry-backoff-ms:1000}") long pollErrorRetryBackoffMs) {
        this.processManager = processManager;
        this.eventSubscriber = eventSubscriber;
        this.objectMapper = objectMapper;
        this.skillFileService = skillFileService;
        this.mcpFileService = mcpFileService;
        this.commandTransport = commandTransport;
        this.runtimeEventService = runtimeEventService;
        this.workItemMapper = workItemMapper;
        this.workspaceResolver = workspaceResolver;
        this.agent = agent;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
        this.waitInterruptedRetryLimit = Math.max(0, waitInterruptedRetryLimit);
        this.waitInterruptedRetryBackoffMs = Math.max(0L, waitInterruptedRetryBackoffMs);
        this.pollErrorRetryLimit = Math.max(0, pollErrorRetryLimit);
        this.pollErrorRetryBackoffMs = Math.max(0L, pollErrorRetryBackoffMs);
    }

    public OpenCodeRuntimeAdapter(
            OpenCodeProcessManager processManager,
            OpenCodeEventSubscriber eventSubscriber,
            ObjectMapper objectMapper,
            OpenCodeSkillFileService skillFileService,
            OpenCodeMcpFileService mcpFileService,
            OpenCodeHttpCommandTransport commandTransport,
            RuntimeEventService runtimeEventService,
            WorkItemMapper workItemMapper,
            ProjectRuntimeWorkspaceResolver workspaceResolver,
            String agent,
            int responseTimeoutSeconds,
            int waitInterruptedRetryLimit,
            long waitInterruptedRetryBackoffMs) {
        this(processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver, agent, responseTimeoutSeconds,
                waitInterruptedRetryLimit, waitInterruptedRetryBackoffMs, 3, 1000);
    }

    public OpenCodeRuntimeAdapter(
            OpenCodeProcessManager processManager,
            OpenCodeEventSubscriber eventSubscriber,
            ObjectMapper objectMapper,
            OpenCodeSkillFileService skillFileService,
            OpenCodeMcpFileService mcpFileService,
            OpenCodeHttpCommandTransport commandTransport,
            RuntimeEventService runtimeEventService,
            WorkItemMapper workItemMapper,
            ProjectRuntimeWorkspaceResolver workspaceResolver,
            String agent,
            int responseTimeoutSeconds) {
        this(processManager, eventSubscriber, objectMapper,
                skillFileService, mcpFileService, commandTransport, runtimeEventService,
                workItemMapper, workspaceResolver, agent, responseTimeoutSeconds, 1, 700, 3, 1000);
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

        Path cwd = resolveRuntimeWorkdir(workItemId);
        String baseUrl = processManager.ensureRunning(cwd);

        String title = "AgentCenter Session";
        if (workItemId != null && !workItemId.isBlank()) {
            title = "AgentCenter · " + workItemId;
        }

        ObjectNode sessionPayload = objectMapper.createObjectNode();
        sessionPayload.put("baseUrl", baseUrl);
        sessionPayload.put("workingDirectory", cwd.toString());
        sessionPayload.put("title", title);
        ArrayNode permissions = sessionPayload.putArray("permission");
        addPermissionRule(permissions, "edit", "*", "ask");
        addPermissionRule(permissions, "external_directory", "*", "ask");

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
        rememberSessionWorkingDirectory(agentSid, opencodeSessionId, cwd);

        eventSubscriber.registerSession(opencodeSessionId, agentSid, baseUrl, cwd.toString());

        log.info("Created opencode session {} → agent session {}", opencodeSessionId, agentSid);
        return opencodeSessionId;
    }

    private void addPermissionRule(ArrayNode permissions, String permission, String pattern, String action) {
        ObjectNode rule = permissions.addObject();
        rule.put("permission", permission);
        rule.put("pattern", pattern);
        rule.put("action", action);
    }

    @Override
    public String ensureSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        if (runtimeSessionId == null || runtimeSessionId.isBlank() || !runtimeSessionId.startsWith("ses_")) {
            return createSession(workItemId, agentSessionId);
        }

        Path cwd = resolveRuntimeWorkdir(workItemId);
        String baseUrl = processManager.ensureRunning(cwd);
        if (commandTransport.fetchMessages(baseUrl, cwd.toString(), runtimeSessionId) == null) {
            log.info("Persisted opencode session {} is not available; creating a replacement for agent session {}",
                    runtimeSessionId, agentSessionId);
            return createSession(workItemId, agentSessionId);
        }

        String agentSid = (agentSessionId != null && !agentSessionId.isBlank())
                ? agentSessionId
                : runtimeSessionId;
        agentToOpencodeSession.put(agentSid, runtimeSessionId);
        agentToOpencodeSession.put(runtimeSessionId, runtimeSessionId);
        rememberSessionWorkingDirectory(agentSid, runtimeSessionId, cwd);
        eventSubscriber.registerSession(runtimeSessionId, agentSid, baseUrl, cwd.toString());
        return runtimeSessionId;
    }

    @Override
    public SkillRunResult runSkill(String sessionId, String skillName, String inputContext) {
        WaitResult waitResult = dispatchPromptAndWait(
                sessionId,
                buildSkillPrompt(skillName, inputContext),
                false);
        String output = waitResult.output();
        if (output == null || output.isBlank()) {
            return failedSkillResult(skillName, waitResult);
        }
        return new SkillRunResult(true, output, "MARKDOWN", null, false);
    }

    /**
     * Sends a skill invocation as 3 separate text parts via prompt_async.
     * This avoids wrapping the instructionPrompt (node state protocol) inside a code block.
     */
    public SkillRunResult runSkill(String sessionId, SkillInvocationRequest request) {
        ArrayNode parts = buildSkillParts(request);
        WaitResult waitResult = dispatchMultiPartPromptAndWait(sessionId, parts, request.skillName());
        String output = waitResult.output();
        if (output == null || output.isBlank()) {
            return failedSkillResult(request.skillName(), waitResult);
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
                - AgentCenter 页面是用户可介入的协作界面；用户可能随时输入补充、调整、继续或接管指令。
                - AgentCenter 工作流只提供调用顺序、工作项信息、上游产物和用户交互回答，不替代 Skill 的判断，也不替代用户本轮输入。
                - 用户说“继续”、补充、调整或追问时，把它当作当前 Skill 的自然多轮输入，直接继续产出、修正或提问。
                - 不要用“等待系统推进”“可在适当时机推进”这类流程占位话术替代对用户本轮输入的实际响应；能继续就直接继续。
                - 如果需要用户继续澄清、选择、确认或授权，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。
                - 有限方案选择必须给出 2-3 个选项；只有开放式补充信息才让用户自由输入。
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
                - AgentCenter 页面是用户可介入的协作界面；用户可能随时输入补充、调整、继续或接管指令。
                - AgentCenter 工作流只提供调用顺序、工作项信息、上游产物和用户交互回答，不替代 Skill 的判断，也不替代用户本轮输入。
                - 用户说“继续”、补充、调整或追问时，把它当作当前 Skill 的自然多轮输入，直接继续产出、修正或提问。
                - 不要用“等待系统推进”“可在适当时机推进”这类流程占位话术替代对用户本轮输入的实际响应；能继续就直接继续。
                - 如果需要用户继续澄清、选择、确认或授权，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。
                - 有限方案选择必须给出 2-3 个选项；只有开放式补充信息才让用户自由输入。
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

    private SkillRunResult failedSkillResult(String skillName, WaitResult waitResult) {
        return new SkillRunResult(
                false,
                null,
                "MARKDOWN",
                failedSkillMessage(skillName, waitResult),
                false
        );
    }

    private String failedSkillMessage(String skillName, WaitResult waitResult) {
        if (waitResult.status() == WaitStatus.INTERRUPTED) {
            return "Agent Runtime 等待 Skill `" + skillName + "` 输出时被中断，已自动重试 "
                    + waitResult.retryCount() + " 次仍未恢复。"
                    + " 这通常意味着 Bridge 服务重启、工作流线程池关闭，或企业运行环境回收了长时间等待的任务。"
                    + " 请检查服务生命周期后重试该节点。";
        }
        if (waitResult.status() == WaitStatus.TRANSPORT_ERROR) {
            return "Agent Runtime 已接收 Skill `" + skillName + "`，但读取 Runtime 输出时连续失败，已自动重试 "
                    + waitResult.retryCount() + " 次仍未恢复。"
                    + " 最后错误：" + nonBlank(waitResult.failureDetail(), "未知错误")
                    + "。请检查 opencode serve 进程、企业代理/网关或网络连接后重试该节点。";
        }
        return "Agent Runtime 已接收 Skill `" + skillName + "`，但在 "
                + responseTimeoutSeconds + " 秒内没有返回可用输出。";
    }

    private WaitResult dispatchPromptAndWait(String sessionId, String userMessage, boolean allowToolOutputFallback) {
        DispatchContext context;
        try {
            context = dispatchPrompt(sessionId, userMessage, false);
        } catch (RuntimePollingException e) {
            return WaitResult.transportError(e.retryCount(), e.getMessage());
        }
        return waitForAssistantTextWithRetry(
                context.baseUrl(),
                context.cwd(),
                context.opencodeSessionId(),
                context.knownMessageIds(),
                allowToolOutputFallback,
                context.cancelGeneration());
    }

    private WaitResult dispatchMultiPartPromptAndWait(String sessionId, ArrayNode parts, String skillName) {
        DispatchContext context;
        try {
            context = dispatchMultiPartPrompt(sessionId, parts, skillName);
        } catch (RuntimePollingException e) {
            return WaitResult.transportError(e.retryCount(), e.getMessage());
        }
        return waitForAssistantTextWithRetry(
                context.baseUrl(),
                context.cwd(),
                context.opencodeSessionId(),
                context.knownMessageIds(),
                false,
                context.cancelGeneration());
    }

    private DispatchContext dispatchPrompt(String sessionId, String userMessage, boolean includeArtifactInstruction) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent session id is required before dispatching to opencode");
        }
        String opencodeSessionId = resolveOpencodeSessionId(sessionId);
        if (opencodeSessionId == null) {
            throw new IllegalArgumentException("No opencode session mapped for agent session: " + sessionId);
        }
        long cancelGeneration = cancelGeneration(opencodeSessionId);

        Path cwd = resolveSessionWorkingDirectory(sessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);
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
        return new DispatchContext(baseUrl, cwd.toString(), opencodeSessionId, knownMessageIds, cancelGeneration);
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
        String opencodeSessionId = resolveOpencodeSessionId(sessionId);
        if (opencodeSessionId == null) {
            throw new IllegalArgumentException("No opencode session mapped for agent session: " + sessionId);
        }
        long cancelGeneration = cancelGeneration(opencodeSessionId);

        Path cwd = resolveSessionWorkingDirectory(sessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);
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
        return new DispatchContext(baseUrl, cwd.toString(), opencodeSessionId, knownMessageIds, cancelGeneration);
    }

    @Override
    public void cancel(String sessionId) {
        String opencodeSessionId = resolveOpencodeSessionId(sessionId);
        if (opencodeSessionId == null) {
            log.info("Cancel ignored because no opencode session is mapped for {}", sessionId);
            return;
        }

        Path cwd = resolveSessionWorkingDirectory(sessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd.toString());

        markCancellation(opencodeSessionId);

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
                RuntimeCommandTypes.CONVERSATION_CANCEL, RuntimeType.OPENCODE,
                opencodeSessionId, payload);
        RuntimeAckEnvelope ack = commandTransport.send(command);
        if (!ack.success()) {
            throw new RuntimeException("opencode abort failed: " + ack.message());
        }

        log.info("Requested abort for opencode session {} (dispatch session {})", opencodeSessionId, sessionId);
    }

    @Override
    public void refreshMcps() {
        recordRuntimeResourceRefresh("MCP", null);
    }

    @Override
    public void refreshSkills(RuntimeSkillSnapshot snapshot) {
        Path projectRoot = snapshot != null && snapshot.projectRoot() != null && !snapshot.projectRoot().isBlank()
                ? Path.of(snapshot.projectRoot())
                : null;
        recordRuntimeResourceRefresh("Skill", projectRoot);
    }

    public void refreshMcps(Path projectWorkdir) {
        recordRuntimeResourceRefresh("MCP", projectWorkdir);
    }

    private void recordRuntimeResourceRefresh(String resourceType, Path projectWorkdir) {
        String projectRoot = projectWorkdir == null ? "<default-runtime-workspace>" : projectWorkdir.toString();
        log.info("{} resources refreshed for {}; keeping opencode serve running so active conversations are not interrupted",
                resourceType, projectRoot);
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

    private String resolveOpencodeSessionId(String sessionId) {
        String opencodeSessionId = agentToOpencodeSession.get(sessionId);
        if (opencodeSessionId == null && sessionId != null && sessionId.startsWith("ses_")) {
            opencodeSessionId = sessionId;
            agentToOpencodeSession.put(sessionId, sessionId);
        }
        return opencodeSessionId;
    }

    private Path resolveRuntimeWorkdir(String workItemId) {
        if (workItemId != null && !workItemId.isBlank()) {
            try {
                WorkItemEntity workItem = workItemMapper.findById(workItemId);
                if (workItem != null) {
                    return workspaceResolver.resolve(workItem.getProjectId());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve runtime workspace for work item {}, falling back to default: {}",
                        workItemId, e.getMessage());
            }
        }
        return processManager.resolveWorkingDirectory();
    }

    private void rememberSessionWorkingDirectory(String agentSessionId, String opencodeSessionId, Path cwd) {
        if (cwd == null) {
            return;
        }
        String value = cwd.toAbsolutePath().normalize().toString();
        if (agentSessionId != null && !agentSessionId.isBlank()) {
            sessionWorkingDirectories.put(agentSessionId, value);
        }
        if (opencodeSessionId != null && !opencodeSessionId.isBlank()) {
            sessionWorkingDirectories.put(opencodeSessionId, value);
        }
    }

    private Path resolveSessionWorkingDirectory(String dispatchSessionId, String opencodeSessionId) {
        String mapped = dispatchSessionId == null ? null : sessionWorkingDirectories.get(dispatchSessionId);
        if ((mapped == null || mapped.isBlank()) && opencodeSessionId != null) {
            mapped = sessionWorkingDirectories.get(opencodeSessionId);
        }
        if (mapped != null && !mapped.isBlank()) {
            return Path.of(mapped).toAbsolutePath().normalize();
        }
        return processManager.resolveWorkingDirectory();
    }

    private long cancelGeneration(String opencodeSessionId) {
        return cancelGenerations.getOrDefault(opencodeSessionId, 0L);
    }

    private void markCancellation(String opencodeSessionId) {
        cancelGenerations.merge(opencodeSessionId, 1L, Long::sum);
    }

    private boolean wasCancelled(String opencodeSessionId, long expectedGeneration) {
        return cancelGeneration(opencodeSessionId) != expectedGeneration;
    }

    public void respondPermission(String opencodeSessionId, String permissionId, boolean approved) {
        respondPermission(opencodeSessionId, permissionId, approved ? "once" : "reject");
    }

    public void respondPermission(String opencodeSessionId, String permissionId, String reply) {
        Path cwd = resolveSessionWorkingDirectory(opencodeSessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("baseUrl", baseUrl);
        payload.put("workingDirectory", cwd.toString());
        payload.put("permissionId", permissionId);
        payload.put("reply", normalizePermissionReply(reply));

        RuntimeCommandEnvelope command = RuntimeCommandEnvelope.of(
            RuntimeCommandTypes.PERMISSION_RESPOND, RuntimeType.OPENCODE,
            opencodeSessionId, payload);

        commandTransport.send(command);
    }

    private String normalizePermissionReply(String reply) {
        if ("always".equals(reply) || "reject".equals(reply)) return reply;
        return "once";
    }

    public void replyQuestion(String opencodeSessionId, String requestId, List<List<String>> answers) {
        Path cwd = resolveSessionWorkingDirectory(opencodeSessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);

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
        Path cwd = resolveSessionWorkingDirectory(opencodeSessionId, opencodeSessionId);
        String baseUrl = processManager.ensureRunning(cwd);

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
        JsonNode messages;
        int retryCount = 0;
        while (true) {
            try {
                messages = commandTransport.fetchMessages(baseUrl, cwd, opencodeSessionId);
                break;
            } catch (RuntimeException e) {
                if (retryCount >= pollErrorRetryLimit) {
                    publishAssistantPollFailed(opencodeSessionId, retryCount, e);
                    throw new RuntimePollingException(errorMessage(e), e, retryCount);
                }
                retryCount += 1;
                publishAssistantPollRetry(opencodeSessionId, retryCount, e);
                if (!sleepBeforePollErrorRetry()) {
                    throw new RuntimePollingException("Interrupted while retrying Runtime message polling", e, retryCount);
                }
            }
        }
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
                log.warn("conversation.dispatch status=missing_agent_mapping dispatchSession={} runtimeSession={}",
                        dispatchSessionId, opencodeSessionId);
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
            logConversationDispatch(
                    dispatchSessionId,
                    agentSessionId,
                    opencodeSessionId,
                    requestPayload.path("parts").isArray() ? requestPayload.path("parts").size() : 0,
                    userPrompt != null ? userPrompt.length() : 0);
        } catch (Exception e) {
            log.warn("conversation.dispatch status=publish_failed runtimeSession={} error={}",
                    opencodeSessionId, errorMessage(e), e);
        }
    }

    private WaitResult waitForAssistantTextWithRetry(String baseUrl, String cwd,
                                                     String opencodeSessionId,
                                                     Set<String> knownMessageIds,
                                                     boolean allowToolOutputFallback,
                                                     long cancelGeneration) {
        long deadline = System.nanoTime() + Duration.ofSeconds(responseTimeoutSeconds).toNanos();
        int retryCount = 0;
        while (true) {
            WaitResult result = waitForAssistantText(
                    baseUrl, cwd, opencodeSessionId, knownMessageIds,
                    allowToolOutputFallback, cancelGeneration, deadline, retryCount);
            if (result.status() != WaitStatus.INTERRUPTED) {
                return result;
            }
            if (retryCount >= waitInterruptedRetryLimit || System.nanoTime() >= deadline) {
                publishAssistantInterrupted(opencodeSessionId, retryCount);
                return result;
            }

            retryCount += 1;
            Thread.interrupted(); // clear the interrupted flag before the retry wait.
            publishAssistantWaitRetry(opencodeSessionId, retryCount);
            if (!sleepBeforeInterruptedRetry()) {
                publishAssistantInterrupted(opencodeSessionId, retryCount);
                return WaitResult.interrupted(retryCount);
            }
        }
    }

    private WaitResult waitForAssistantText(String baseUrl, String cwd,
                                            String opencodeSessionId,
                                            Set<String> knownMessageIds,
                                            boolean allowToolOutputFallback,
                                            long cancelGeneration,
                                            long deadline,
                                            int retryCount) {
        int pollErrorRetryCount = 0;
        while (System.nanoTime() < deadline) {
            if (wasCancelled(opencodeSessionId, cancelGeneration)) {
                return WaitResult.output(USER_CANCELLED_NODE_STATE, retryCount);
            }
            JsonNode messages;
            try {
                messages = commandTransport.fetchMessages(baseUrl, cwd, opencodeSessionId);
                pollErrorRetryCount = 0;
            } catch (RuntimeException e) {
                if (pollErrorRetryCount >= pollErrorRetryLimit || System.nanoTime() >= deadline) {
                    publishAssistantPollFailed(opencodeSessionId, pollErrorRetryCount, e);
                    return WaitResult.transportError(pollErrorRetryCount, errorMessage(e));
                }
                pollErrorRetryCount += 1;
                publishAssistantPollRetry(opencodeSessionId, pollErrorRetryCount, e);
                if (!sleepBeforePollErrorRetry()) {
                    return WaitResult.interrupted(retryCount);
                }
                continue;
            }
            String text = extractNewAssistantText(messages, knownMessageIds);
            if (text != null && !text.isBlank()) {
                publishAssistantCompleted(opencodeSessionId);
                return WaitResult.output(text, retryCount);
            }
            if (allowToolOutputFallback) {
                String toolOutput = extractNewCompletedToolOutput(messages, knownMessageIds);
                if (toolOutput != null && !toolOutput.isBlank()) {
                    return WaitResult.output(toolOutput, retryCount);
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return WaitResult.interrupted(retryCount);
            }
        }
        publishAssistantTimeout(opencodeSessionId);
        return WaitResult.timeout(retryCount);
    }

    private boolean sleepBeforeInterruptedRetry() {
        if (waitInterruptedRetryBackoffMs == 0L) {
            return true;
        }
        try {
            Thread.sleep(waitInterruptedRetryBackoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean sleepBeforePollErrorRetry() {
        if (pollErrorRetryBackoffMs == 0L) {
            return true;
        }
        try {
            Thread.sleep(pollErrorRetryBackoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void publishAssistantTimeout(String opencodeSessionId) {
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            log.warn("conversation.timeout status=missing_agent_mapping runtimeSession={} timeoutSeconds={}",
                    opencodeSessionId, responseTimeoutSeconds);
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
            log.warn("conversation.timeout status=published session={} runtimeSession={} workItem={} workflow={} node={} timeoutSeconds={} errorCode=RUNTIME_TIMEOUT recoverable=true",
                    agentSessionId,
                    opencodeSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    responseTimeoutSeconds);
        } catch (Exception e) {
            log.warn("conversation.timeout status=publish_failed runtimeSession={} error={}",
                    opencodeSessionId, errorMessage(e), e);
        }
    }

    private void publishAssistantPollRetry(String opencodeSessionId, int retryCount, Exception error) {
        publishAssistantPollFailureEvent(
                opencodeSessionId,
                RuntimeEventType.PROCESS_TRACE,
                "retrying",
                "读取 Runtime 输出失败，正在自动重试",
                retryCount,
                true,
                error);
    }

    private void publishAssistantPollFailed(String opencodeSessionId, int retryCount, Exception error) {
        publishAssistantPollFailureEvent(
                opencodeSessionId,
                RuntimeEventType.ERROR,
                "failed",
                "读取 Runtime 输出失败",
                retryCount,
                false,
                error);
        publishAssistantPollFailureEvent(
                opencodeSessionId,
                RuntimeEventType.PROCESS_TRACE,
                "failed",
                "读取 Runtime 输出失败",
                retryCount,
                false,
                error);
    }

    private void publishAssistantPollFailureEvent(String opencodeSessionId,
                                                  RuntimeEventType eventType,
                                                  String status,
                                                  String title,
                                                  int retryCount,
                                                  boolean retrying,
                                                  Exception error) {
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            log.warn("conversation.poll_failed status=missing_agent_mapping runtimeSession={} retryCount={} retryLimit={} error={}",
                    opencodeSessionId, retryCount, pollErrorRetryLimit, errorMessage(error));
            return;
        }
        String summary = retrying
                ? "读取 Agent Runtime 输出时失败，正在第 " + retryCount + " 次自动重试。"
                : "读取 Agent Runtime 输出时失败，已自动重试 " + retryCount + " 次仍未恢复。";
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("kind", retrying ? "retry" : "error");
            payload.put("status", status);
            payload.put("title", title);
            payload.put("summary", summary);
            payload.put("reason", "RUNTIME_POLL_FAILED");
            payload.put("errorCode", "RUNTIME_POLL_FAILED");
            payload.put("errorMessage", errorMessage(error));
            payload.put("recoverable", true);
            payload.put("retryAttempt", retryCount);
            payload.put("retryLimit", pollErrorRetryLimit);
            payload.put("rawEventType", "session.messages.poll_failed");
            if (eventType == RuntimeEventType.PROCESS_TRACE && !retrying) {
                payload.put("visibility", "public_summary");
            }
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    eventType,
                    RuntimeEventSource.OPENCODE,
                    payload.toString(),
                    null
            ));
            log.warn("conversation.poll_failed status={} session={} runtimeSession={} retryCount={} retryLimit={} errorCode=RUNTIME_POLL_FAILED recoverable=true error={}",
                    status, agentSessionId, opencodeSessionId, retryCount, pollErrorRetryLimit, errorMessage(error));
        } catch (Exception e) {
            log.warn("conversation.poll_failed status=publish_failed runtimeSession={} error={}",
                    opencodeSessionId, errorMessage(e), e);
        }
    }

    private void publishAssistantWaitRetry(String opencodeSessionId, int retryCount) {
        publishAssistantWaitInterruptionEvent(
                opencodeSessionId,
                RuntimeEventType.PROCESS_TRACE,
                "retrying",
                "Runtime 等待被中断，正在自动重试",
                retryCount,
                true);
    }

    private void publishAssistantInterrupted(String opencodeSessionId, int retryCount) {
        publishAssistantWaitInterruptionEvent(
                opencodeSessionId,
                RuntimeEventType.ERROR,
                "failed",
                "Runtime 等待被中断",
                retryCount,
                false);
        publishAssistantWaitInterruptionEvent(
                opencodeSessionId,
                RuntimeEventType.PROCESS_TRACE,
                "failed",
                "Runtime 等待被中断",
                retryCount,
                false);
    }

    private void publishAssistantWaitInterruptionEvent(String opencodeSessionId,
                                                       RuntimeEventType eventType,
                                                       String status,
                                                       String title,
                                                       int retryCount,
                                                       boolean retrying) {
        String agentSessionId = eventSubscriber.getAgentSessionId(opencodeSessionId);
        if (agentSessionId == null || agentSessionId.isBlank()) {
            log.warn("conversation.wait_interrupted status=missing_agent_mapping runtimeSession={} retryCount={} retryLimit={}",
                    opencodeSessionId, retryCount, waitInterruptedRetryLimit);
            return;
        }
        String summary = retrying
                ? "Agent Runtime 等待输出时被中断，正在第 " + retryCount + " 次自动重试。"
                : "Agent Runtime 等待输出时被中断，已自动重试 " + retryCount + " 次仍未恢复。";
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("kind", retrying ? "retry" : "error");
            payload.put("status", status);
            payload.put("title", title);
            payload.put("summary", summary);
            payload.put("reason", "RUNTIME_WAIT_INTERRUPTED");
            payload.put("errorCode", "RUNTIME_WAIT_INTERRUPTED");
            payload.put("errorMessage", summary);
            payload.put("recoverable", true);
            payload.put("retryAttempt", retryCount);
            payload.put("retryLimit", waitInterruptedRetryLimit);
            payload.put("rawEventType", "session.messages.wait_interrupted");
            if (eventType == RuntimeEventType.PROCESS_TRACE && !retrying) {
                payload.put("visibility", "public_summary");
            }
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    agentSessionId,
                    eventSubscriber.getWorkItemId(agentSessionId),
                    eventSubscriber.getWorkflowInstanceId(agentSessionId),
                    eventSubscriber.getWorkflowNodeInstanceId(agentSessionId),
                    eventType,
                    RuntimeEventSource.OPENCODE,
                    payload.toString(),
                    null
            ));
            log.warn("conversation.wait_interrupted status={} session={} runtimeSession={} retryCount={} retryLimit={} recoverable=true",
                    status, agentSessionId, opencodeSessionId, retryCount, waitInterruptedRetryLimit);
        } catch (Exception e) {
            log.warn("conversation.wait_interrupted status=publish_failed runtimeSession={} error={}",
                    opencodeSessionId, errorMessage(e), e);
        }
    }

    private void logConversationDispatch(String dispatchSessionId,
                                         String agentSessionId,
                                         String opencodeSessionId,
                                         int partCount,
                                         int promptChars) {
        traceInfo("conversation.dispatch status=sent dispatchSession={} session={} runtimeSession={} agent={} parts={} promptChars={}",
                dispatchSessionId,
                agentSessionId,
                opencodeSessionId,
                agent,
                partCount,
                promptChars);
    }

    private void traceInfo(String message, Object... args) {
        if (conversationLogEnabled) {
            log.info(message, args);
            return;
        }
        log.debug(message, args);
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? clipForLog(error.getMessage())
                : error.getClass().getSimpleName();
    }

    private String clipForLog(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= LOG_FIELD_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, LOG_FIELD_LIMIT) + "...";
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

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DispatchContext(String baseUrl, String cwd,
                                   String opencodeSessionId,
                                   Set<String> knownMessageIds,
                                   long cancelGeneration) {}

    private enum WaitStatus {
        OUTPUT,
        TIMEOUT,
        INTERRUPTED,
        TRANSPORT_ERROR
    }

    private record WaitResult(WaitStatus status, String output, int retryCount, String failureDetail) {
        static WaitResult output(String output, int retryCount) {
            return new WaitResult(WaitStatus.OUTPUT, output, retryCount, null);
        }

        static WaitResult timeout(int retryCount) {
            return new WaitResult(WaitStatus.TIMEOUT, null, retryCount, null);
        }

        static WaitResult interrupted(int retryCount) {
            return new WaitResult(WaitStatus.INTERRUPTED, null, retryCount, null);
        }

        static WaitResult transportError(int retryCount, String failureDetail) {
            return new WaitResult(WaitStatus.TRANSPORT_ERROR, null, retryCount, failureDetail);
        }
    }

    private static final class RuntimePollingException extends RuntimeException {
        private final int retryCount;

        private RuntimePollingException(String message, Throwable cause, int retryCount) {
            super(message, cause);
            this.retryCount = retryCount;
        }

        private int retryCount() {
            return retryCount;
        }
    }
}
