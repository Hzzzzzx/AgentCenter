package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.application.runtime.*;
import com.agentcenter.bridge.domain.runtime.RuntimeType;

class OpenCodeRuntimeProviderTest {

    private OpenCodeRuntimeAdapter adapter;
    private OpenCodeRuntimeProvider provider;

    @BeforeEach
    void setUp() {
        adapter = mock(OpenCodeRuntimeAdapter.class);
        provider = new OpenCodeRuntimeProvider(adapter);
    }

    @Test
    void runtimeTypeIsOpencode() {
        assertEquals(RuntimeType.OPENCODE, provider.runtimeType());
    }

    @Test
    void descriptorHasCorrectFields() {
        RuntimeDescriptor desc = provider.descriptor();
        assertEquals("OpenCode", desc.displayName());
        assertEquals("HTTP+SSE", desc.transportType());
        assertNotNull(desc.capabilities());
    }

    @Test
    void capabilitiesDeclaresOpenCodeSupport() {
        RuntimeCapabilities caps = provider.capabilities();
        assertTrue(caps.conversationStreaming());
        assertTrue(caps.skillLifecycle());
        assertTrue(caps.mcpLifecycle());
        assertTrue(caps.cancelSupported());
        assertEquals(RuntimeCapabilities.HTTP, caps.commandTransport());
        assertEquals(RuntimeCapabilities.SSE, caps.eventTransport());
        assertEquals(RuntimeCapabilities.LOCAL_FILE, caps.resourceMutationMode());
        assertFalse(caps.supportsAsyncOperations());
    }

    @Test
    void createSessionDelegates() {
        when(adapter.createSessionWithContext(any(RuntimeOperationContext.class))).thenReturn("ses_123");
        assertEquals("ses_123", provider.createSession("w1", "a1"));
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(adapter).createSessionWithContext(captor.capture());
        assertEquals("w1", captor.getValue().workItemId());
        assertEquals("a1", captor.getValue().agentSessionId());
    }

    @Test
    void ensureSessionDelegates() {
        when(adapter.ensureSessionWithContext(any(RuntimeOperationContext.class))).thenReturn("ses_123");
        assertEquals("ses_123", provider.ensureSession("w1", "a1", "ses_123"));
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(adapter).ensureSessionWithContext(captor.capture());
        assertEquals("w1", captor.getValue().workItemId());
        assertEquals("a1", captor.getValue().agentSessionId());
        assertEquals("ses_123", captor.getValue().runtimeSessionId());
    }

    @Test
    void runSkillDelegates() {
        SkillRunResult expected = new SkillRunResult(true, "out", "MD", null, false);
        when(adapter.runSkill("ses_1", "skill", "ctx")).thenReturn(expected);
        assertEquals(expected, provider.runSkill("ses_1", "skill", "ctx"));
    }

    @Test
    void runSkillWithRequest_delegatesToAdapter() {
        SkillRunResult expected = new SkillRunResult(true, "out", "MD", null, false);
        SkillInvocationRequest request = new SkillInvocationRequest(
                "skill", "user prompt", "instruction prompt", null);
        when(adapter.runSkillWithContext(any(RuntimeOperationContext.class), eq(request)))
                .thenReturn(expected);

        SkillRunResult result = provider.runSkill("ses_1", request);
        assertEquals(expected, result);
    }

    @Test
    void runSkillWithRequest_noInstruction_delegatesToAdapter() {
        SkillRunResult expected = new SkillRunResult(true, "out", "MD", null, false);
        SkillInvocationRequest request = new SkillInvocationRequest(
                "skill", "user prompt", null, null);
        when(adapter.runSkillWithContext(any(RuntimeOperationContext.class), eq(request)))
                .thenReturn(expected);

        SkillRunResult result = provider.runSkill("ses_1", request);
        assertEquals(expected, result);
    }

    @Test
    void runSkillWithRequest_blankInstruction_delegatesToAdapter() {
        SkillRunResult expected = new SkillRunResult(true, "out", "MD", null, false);
        SkillInvocationRequest request = new SkillInvocationRequest(
                "skill", "user prompt", "  ", null);
        when(adapter.runSkillWithContext(any(RuntimeOperationContext.class), eq(request)))
                .thenReturn(expected);

        SkillRunResult result = provider.runSkill("ses_1", request);
        assertEquals(expected, result);
    }

    @Test
    void sendMessageDelegates() {
        provider.sendMessage("ses_1", "hello");
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(adapter).sendMessageWithContext(captor.capture(), eq("hello"));
        assertEquals("ses_1", captor.getValue().runtimeSessionId());
    }

    @Test
    void cancelDelegates() {
        provider.cancel("ses_1");
        ArgumentCaptor<RuntimeOperationContext> captor = ArgumentCaptor.forClass(RuntimeOperationContext.class);
        verify(adapter).cancelWithContext(captor.capture());
        assertEquals("ses_1", captor.getValue().runtimeSessionId());
    }

    @Test
    void refreshSkillsDelegates() {
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(null, null, List.of());
        provider.refreshSkills(snapshot);
        verify(adapter).refreshSkills(snapshot);
    }

    @Test
    void refreshMcpsDelegates() {
        provider.refreshMcps();
        verify(adapter).refreshMcps();
    }
}
