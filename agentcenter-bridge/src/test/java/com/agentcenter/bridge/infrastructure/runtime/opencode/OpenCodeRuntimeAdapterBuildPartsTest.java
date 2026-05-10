package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeHttpCommandTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import static org.mockito.Mockito.*;

class OpenCodeRuntimeAdapterBuildPartsTest {

    private OpenCodeRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        OpenCodeProcessManager processManager = mock(OpenCodeProcessManager.class);
        OpenCodeEventSubscriber eventSubscriber = mock(OpenCodeEventSubscriber.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OpenCodeSkillFileService skillFileService = mock(OpenCodeSkillFileService.class);
        OpenCodeMcpFileService mcpFileService = mock(OpenCodeMcpFileService.class);
        OpenCodeHttpCommandTransport commandTransport = mock(OpenCodeHttpCommandTransport.class);
        RuntimeEventService runtimeEventService = mock(RuntimeEventService.class);

        adapter = new OpenCodeRuntimeAdapter(
                processManager, eventSubscriber, objectMapper, skillFileService,
                mcpFileService, commandTransport, runtimeEventService, "build", 180);
    }

    @Test
    void buildSkillParts_withInstructionPrompt_returns3Parts() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "my-skill", "user input text", "# Node State\n\nSome instructions", null);

        ArrayNode parts = adapter.buildSkillParts(request);

        assertEquals(3, parts.size());

        assertEquals("text", parts.get(0).get("type").asText());
        String part1 = parts.get(0).get("text").asText();
        assertTrue(part1.contains("my-skill"));
        assertTrue(part1.contains("工作方式"));
        assertTrue(part1.contains("运行边界"));
        assertTrue(part1.contains("只读取、搜索和修改该工作目录内的文件"));
        assertTrue(part1.contains("不要访问、搜索或引用 AgentCenter 源码目录"));
        assertFalse(part1.contains("```text"));

        assertEquals("text", parts.get(1).get("type").asText());
        String part2 = parts.get(1).get("text").asText();
        assertTrue(part2.startsWith("输入上下文："));
        assertTrue(part2.contains("```text\nuser input text\n```"));

        assertEquals("text", parts.get(2).get("type").asText());
        String part3 = parts.get(2).get("text").asText();
        assertEquals("# Node State\n\nSome instructions", part3);
        assertFalse(part3.contains("```"));
    }

    @Test
    void buildSkillParts_withoutInstructionPrompt_returns2Parts() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "my-skill", "user input text", null, null);

        ArrayNode parts = adapter.buildSkillParts(request);

        assertEquals(2, parts.size());
        assertEquals("text", parts.get(0).get("type").asText());
        assertEquals("text", parts.get(1).get("type").asText());
    }

    @Test
    void buildSkillParts_withBlankInstructionPrompt_returns2Parts() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "my-skill", "user input text", "   ", null);

        ArrayNode parts = adapter.buildSkillParts(request);

        assertEquals(2, parts.size());
    }

    @Test
    void buildSkillParts_part1_containsSkillInvocationInstruction() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "code-review", "input", null, null);

        ArrayNode parts = adapter.buildSkillParts(request);

        String part1 = parts.get(0).get("text").asText();
        assertTrue(part1.contains("请使用当前 Agent Runtime 中的 Skill `code-review` 处理下面的用户输入。"));
        assertTrue(part1.contains("优先遵循 Skill 自身说明和当前会话上下文"));
        assertTrue(part1.contains("AgentCenter 工作流只提供调用顺序"));
        assertTrue(part1.contains("如果任务需要工作目录之外的信息，请先说明缺失信息"));
    }

    @Test
    void buildSkillParts_part2_wrapsUserInputInCodeBlock() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "my-skill", "the user's actual input", null, null);

        ArrayNode parts = adapter.buildSkillParts(request);

        String part2 = parts.get(1).get("text").asText();
        assertTrue(part2.contains("```text\nthe user's actual input\n```"));
    }

    @Test
    void buildSkillParts_instructionPromptIsPlain_noCodeBlock() {
        SkillInvocationRequest request = new SkillInvocationRequest(
                "skill", "input", "# AGENTCENTER_NODE_STATE\n\n- item 1\n- item 2", null);

        ArrayNode parts = adapter.buildSkillParts(request);

        String part3 = parts.get(2).get("text").asText();
        assertEquals("# AGENTCENTER_NODE_STATE\n\n- item 1\n- item 2", part3);
        assertFalse(part3.startsWith("```"));
        assertFalse(part3.endsWith("```"));
    }
}
