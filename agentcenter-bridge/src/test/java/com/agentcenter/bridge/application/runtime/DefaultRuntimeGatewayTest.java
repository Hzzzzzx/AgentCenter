package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;

class DefaultRuntimeGatewayTest {

    private DefaultRuntimeProviderRegistry registry;
    private RuntimeProvider provider;
    private RuntimeOperationService operationService;
    private DefaultRuntimeGateway gateway;
    private int idCounter = 0;

    @BeforeEach
    void setUp() {
        provider = mock(RuntimeProvider.class);
        when(provider.runtimeType()).thenReturn(RuntimeType.OPENCODE);
        registry = new DefaultRuntimeProviderRegistry(List.of(provider));
        operationService = mock(RuntimeOperationService.class);
        gateway = new DefaultRuntimeGateway(registry, operationService);
    }

    private RuntimeOperationEntity createMockEntity() {
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId("op_test_" + idCounter++);
        entity.setStatus(RuntimeOperationStatus.CREATED.name());
        return entity;
    }

    private void stubOperationCreation() {
        when(operationService.createOperation(
                eq("default"), eq("OPENCODE"), anyString(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), anyString(), any(), isNull(), isNull(), eq("system")))
                .thenReturn(createMockEntity());
    }

    // --- Untracked methods (no operation created) ---

    @Test
    void ensureSessionDelegates() {
        when(provider.ensureSessionWithContext(any(RuntimeOperationContext.class))).thenReturn("r1");
        String result = gateway.ensureSession(RuntimeType.OPENCODE, "w1", "a1", "r1");
        assertEquals("r1", result);
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(provider).ensureSessionWithContext(captor.capture());
        assertEquals("w1", captor.getValue().workItemId());
        assertEquals("a1", captor.getValue().agentSessionId());
        assertEquals("r1", captor.getValue().runtimeSessionId());
        verifyNoInteractions(operationService);
    }

    @Test
    void sendMessageDelegates() {
        gateway.sendMessage(RuntimeType.OPENCODE, "ses_1", "hello");
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(provider).sendMessageWithContext(captor.capture(), eq("hello"));
        assertEquals("ses_1", captor.getValue().runtimeSessionId());
        verifyNoInteractions(operationService);
    }

    @Test
    void runSkillDelegates() {
        SkillRunResult expected = new SkillRunResult(true, "output", "MARKDOWN", null, false);
        when(provider.runSkillWithContext(any(RuntimeOperationContext.class), any(SkillInvocationRequest.class)))
                .thenReturn(expected);
        SkillRunResult result = gateway.runSkill(RuntimeType.OPENCODE, "ses_1", "skill1", "ctx");
        assertEquals(expected, result);
        verifyNoInteractions(operationService);
    }

    @Test
    void runSkillWithRequestDelegatesToProvider() {
        SkillRunResult expected = new SkillRunResult(true, "output", "MARKDOWN", null, false);
        SkillInvocationRequest request = SkillInvocationRequest.userPromptInjection("skill1", "user prompt", "instruction");
        when(provider.runSkillWithContext(any(RuntimeOperationContext.class), eq(request))).thenReturn(expected);

        SkillRunResult result = gateway.runSkill(RuntimeType.OPENCODE, "ses_1", request);

        assertEquals(expected, result);
        ArgumentCaptor<RuntimeOperationContext> contextCaptor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(provider).runSkillWithContext(contextCaptor.capture(), eq(request));
        assertEquals("ses_1", contextCaptor.getValue().runtimeSessionId());
        verifyNoInteractions(operationService);
    }

    @Test
    void runSkillWithLegacySignatureDelegatesViaRequest() {
        SkillRunResult expected = new SkillRunResult(true, "output", "MARKDOWN", null, false);
        when(provider.runSkillWithContext(any(RuntimeOperationContext.class), any(SkillInvocationRequest.class)))
                .thenReturn(expected);

        SkillRunResult result = gateway.runSkill(RuntimeType.OPENCODE, "ses_1", "skill1", "ctx");

        assertEquals(expected, result);
        ArgumentCaptor<SkillInvocationRequest> captor = ArgumentCaptor.forClass(SkillInvocationRequest.class);
        verify(provider).runSkillWithContext(any(RuntimeOperationContext.class), captor.capture());
        assertEquals("skill1", captor.getValue().skillName());
        assertEquals("ctx", captor.getValue().userPrompt());
        assertNull(captor.getValue().instructionPrompt());
        assertEquals(RuntimeInstructionInjectionMode.USER_PROMPT, captor.getValue().injectionMode());
        verifyNoInteractions(operationService);
    }

    @Test
    void describeReturnsDescriptor() {
        RuntimeDescriptor desc = new RuntimeDescriptor("OpenCode", "HTTP+SSE", "desc",
                new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false));
        when(provider.descriptor()).thenReturn(desc);
        RuntimeDescriptor result = gateway.describe(RuntimeType.OPENCODE);
        assertEquals(desc, result);
        verifyNoInteractions(operationService);
    }

    @Test
    void capabilitiesDelegates() {
        RuntimeCapabilities caps = new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false);
        when(provider.capabilities()).thenReturn(caps);
        RuntimeCapabilities result = gateway.capabilities(RuntimeType.OPENCODE);
        assertEquals(caps, result);
        verifyNoInteractions(operationService);
    }

    @Test
    void cancelDelegates() {
        gateway.cancel(RuntimeType.OPENCODE, "ses_1");
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(provider).cancelWithContext(captor.capture());
        assertEquals("ses_1", captor.getValue().runtimeSessionId());
        verifyNoInteractions(operationService);
    }

    // --- Tracked methods: success path ---

    @Test
    void installSkillCreatesAndTracksOperation() {
        stubOperationCreation();
        when(provider.installSkill(isNull(), eq("mySkill"), isNull())).thenReturn("installed_ok");

        String result = gateway.installSkill(RuntimeType.OPENCODE, "mySkill", null);

        assertEquals("installed_ok", result);

        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.SKILL_INSTALL.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("skill"), eq("mySkill"), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void installSkillTracksFailedOperationOnException() {
        stubOperationCreation();
        when(provider.installSkill(isNull(), eq("badSkill"), isNull())).thenThrow(new RuntimeException("install failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> gateway.installSkill(RuntimeType.OPENCODE, "badSkill", null));
        assertEquals("install failed", ex.getMessage());

        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transitionToFailed(startsWith("op_test_"), eq("PROVIDER_ERROR"), eq("install failed"));
        verify(operationService, never()).transition(anyString(), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void deleteSkillFilesCreatesAndTracksOperation() {
        stubOperationCreation();

        gateway.deleteSkillFiles(RuntimeType.OPENCODE, "skills/mySkill", "mySkill");

        verify(provider).deleteSkillFiles(isNull(), eq("skills/mySkill"), eq("mySkill"));
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.SKILL_DELETE.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("skill"), eq("mySkill"), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void refreshSkillsCreatesAndTracksOperation() {
        stubOperationCreation();
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(null, null, List.of());

        gateway.refreshSkills(RuntimeType.OPENCODE, snapshot);

        verify(provider).refreshSkills(snapshot);
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.SKILL_SCAN.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("skill"), isNull(), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void refreshMcpsCreatesAndTracksOperation() {
        stubOperationCreation();

        gateway.refreshMcps(RuntimeType.OPENCODE);

        verify(provider).refreshMcps(isNull());
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.MCP_REFRESH.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("mcp"), eq("mcp_config"), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void writeMcpConfigCreatesAndTracksOperation() {
        stubOperationCreation();
        Map<String, Object> config = Map.of("servers", List.of());

        gateway.writeMcpConfig(RuntimeType.OPENCODE, config);

        verify(provider).writeMcpConfig(isNull(), eq(config));
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.MCP_WRITE_CONFIG.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("mcp"), eq("mcp_config"), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void readMcpConfigCreatesAndTracksOperation() {
        stubOperationCreation();
        Map<String, Object> config = Map.of("key", "value");
        when(provider.readMcpConfig(isNull())).thenReturn(config);

        Map<String, Object> result = gateway.readMcpConfig(RuntimeType.OPENCODE);

        assertEquals(config, result);
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.MCP_READ_CONFIG.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("mcp"), eq("mcp_config"), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }

    @Test
    void scanSkillsCreatesAndTracksOperation() {
        stubOperationCreation();
        when(provider.scanSkills(isNull())).thenReturn(List.of());

        var result = gateway.scanSkills(RuntimeType.OPENCODE);

        assertEquals(List.of(), result);
        verify(operationService).createOperation(
                eq("default"), eq("OPENCODE"), eq(RuntimeOperationType.SKILL_SCAN.value()),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("skill"), isNull(), isNull(), isNull(), eq("system"));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.DISPATCHING));
        verify(operationService).transition(startsWith("op_test_"), eq(RuntimeOperationStatus.SUCCEEDED));
    }
}
