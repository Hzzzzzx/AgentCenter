package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertFalse(caps.skillLifecycle());
        assertFalse(caps.mcpLifecycle());
        assertTrue(caps.cancelSupported());
    }

    @Test
    void createSessionDelegates() {
        when(adapter.createSession("w1", "a1")).thenReturn("ses_123");
        assertEquals("ses_123", provider.createSession("w1", "a1"));
    }

    @Test
    void ensureSessionDelegates() {
        when(adapter.ensureSession("w1", "a1", "ses_123")).thenReturn("ses_123");
        assertEquals("ses_123", provider.ensureSession("w1", "a1", "ses_123"));
    }

    @Test
    void runSkillDelegates() {
        SkillRunResult expected = new SkillRunResult(true, "out", "MD", null, false);
        when(adapter.runSkill("ses_1", "skill", "ctx")).thenReturn(expected);
        assertEquals(expected, provider.runSkill("ses_1", "skill", "ctx"));
    }

    @Test
    void sendMessageDelegates() {
        provider.sendMessage("ses_1", "hello");
        verify(adapter).sendMessage("ses_1", "hello");
    }

    @Test
    void cancelDelegates() {
        provider.cancel("ses_1");
        verify(adapter).cancel("ses_1");
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
