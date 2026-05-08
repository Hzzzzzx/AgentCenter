package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

class DefaultRuntimeProviderRegistryTest {

    private static class StubProvider implements RuntimeProvider {
        private final RuntimeType type;
        StubProvider(RuntimeType type) { this.type = type; }
        @Override public RuntimeType runtimeType() { return type; }
        @Override public RuntimeDescriptor descriptor() { return new RuntimeDescriptor("Stub", "TEST", "test", capabilities()); }
        @Override public RuntimeCapabilities capabilities() { return new RuntimeCapabilities(false, false, false, false, null, null, null, false); }
        @Override public String createSession(String w, String a) { return "stub-session"; }
        @Override public String ensureSession(String w, String a, String r) { return r != null ? r : "stub-session"; }
        @Override public SkillRunResult runSkill(String s, String sk, String i) { return new SkillRunResult(true, "ok", "TEXT", null, false); }
        @Override public void sendMessage(String s, String m) {}
        @Override public void cancel(String s) {}
        @Override public void refreshSkills(RuntimeSkillSnapshot sn) {}
        @Override public void refreshMcps() {}
        @Override public java.util.List<com.agentcenter.bridge.api.dto.RuntimeSkillDto> scanSkills() { return java.util.List.of(); }
        @Override public String installSkill(String name, java.nio.file.Path dir) { return ""; }
        @Override public void deleteSkillFiles(String rel, String name) {}
        @Override public String getSkillsRootPath() { return ""; }
        @Override public java.util.Map<String, Object> readMcpConfig() { return java.util.Map.of(); }
        @Override public void writeMcpConfig(java.util.Map<String, Object> config) {}
    }

    @Test
    void registersAndLooksUpProvider() {
        StubProvider opencode = new StubProvider(RuntimeType.OPENCODE);
        DefaultRuntimeProviderRegistry registry = new DefaultRuntimeProviderRegistry(List.of(opencode));
        assertSame(opencode, registry.getProvider(RuntimeType.OPENCODE));
    }

    @Test
    void rejectsDuplicateProviders() {
        StubProvider p1 = new StubProvider(RuntimeType.OPENCODE);
        StubProvider p2 = new StubProvider(RuntimeType.OPENCODE);
        assertThrows(IllegalStateException.class, () -> new DefaultRuntimeProviderRegistry(List.of(p1, p2)));
    }

    @Test
    void throwsForUnregisteredType() {
        DefaultRuntimeProviderRegistry registry = new DefaultRuntimeProviderRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.getProvider(RuntimeType.OPENCODE));
    }
}
