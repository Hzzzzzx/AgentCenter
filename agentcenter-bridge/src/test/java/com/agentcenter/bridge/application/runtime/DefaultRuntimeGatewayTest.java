package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

class DefaultRuntimeGatewayTest {

    private DefaultRuntimeProviderRegistry registry;
    private RuntimeProvider provider;
    private DefaultRuntimeGateway gateway;

    @BeforeEach
    void setUp() {
        provider = mock(RuntimeProvider.class);
        when(provider.runtimeType()).thenReturn(RuntimeType.OPENCODE);
        registry = new DefaultRuntimeProviderRegistry(List.of(provider));
        gateway = new DefaultRuntimeGateway(registry);
    }

    @Test
    void ensureSessionDelegates() {
        when(provider.ensureSession("w1", "a1", "r1")).thenReturn("r1");
        String result = gateway.ensureSession(RuntimeType.OPENCODE, "w1", "a1", "r1");
        assertEquals("r1", result);
        verify(provider).ensureSession("w1", "a1", "r1");
    }

    @Test
    void sendMessageDelegates() {
        gateway.sendMessage(RuntimeType.OPENCODE, "ses_1", "hello");
        verify(provider).sendMessage("ses_1", "hello");
    }

    @Test
    void runSkillDelegates() {
        SkillRunResult expected = new SkillRunResult(true, "output", "MARKDOWN", null, false);
        when(provider.runSkill("ses_1", "skill1", "ctx")).thenReturn(expected);
        SkillRunResult result = gateway.runSkill(RuntimeType.OPENCODE, "ses_1", "skill1", "ctx");
        assertEquals(expected, result);
    }

    @Test
    void refreshSkillsDelegates() {
        RuntimeSkillSnapshot snapshot = new RuntimeSkillSnapshot(null, null, List.of());
        gateway.refreshSkills(RuntimeType.OPENCODE, snapshot);
        verify(provider).refreshSkills(snapshot);
    }

    @Test
    void refreshMcpsDelegates() {
        gateway.refreshMcps(RuntimeType.OPENCODE);
        verify(provider).refreshMcps();
    }

    @Test
    void describeReturnsDescriptor() {
        RuntimeDescriptor desc = new RuntimeDescriptor("OpenCode", "HTTP+SSE", "desc",
                new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false));
        when(provider.descriptor()).thenReturn(desc);
        RuntimeDescriptor result = gateway.describe(RuntimeType.OPENCODE);
        assertEquals(desc, result);
    }

    @Test
    void capabilitiesDelegates() {
        RuntimeCapabilities caps = new RuntimeCapabilities(true, true, true, true, RuntimeCapabilities.HTTP, RuntimeCapabilities.SSE, RuntimeCapabilities.LOCAL_FILE, false);
        when(provider.capabilities()).thenReturn(caps);
        RuntimeCapabilities result = gateway.capabilities(RuntimeType.OPENCODE);
        assertEquals(caps, result);
    }

    @Test
    void cancelDelegates() {
        gateway.cancel(RuntimeType.OPENCODE, "ses_1");
        verify(provider).cancel("ses_1");
    }
}
