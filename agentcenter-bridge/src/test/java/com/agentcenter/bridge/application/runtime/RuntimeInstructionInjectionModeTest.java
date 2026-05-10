package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RuntimeInstructionInjectionModeTest {

    @Test
    void hasAllExpectedValues() {
        RuntimeInstructionInjectionMode[] values = RuntimeInstructionInjectionMode.values();
        assertEquals(5, values.length);
        assertArrayEquals(new RuntimeInstructionInjectionMode[]{
                RuntimeInstructionInjectionMode.USER_PROMPT,
                RuntimeInstructionInjectionMode.SYSTEM_PROMPT,
                RuntimeInstructionInjectionMode.DEVELOPER_PROMPT,
                RuntimeInstructionInjectionMode.STRUCTURED_OUTPUT,
                RuntimeInstructionInjectionMode.METADATA
        }, values);
    }

    @Test
    void valueOfReturnsCorrectEnum() {
        assertEquals(RuntimeInstructionInjectionMode.USER_PROMPT, RuntimeInstructionInjectionMode.valueOf("USER_PROMPT"));
        assertEquals(RuntimeInstructionInjectionMode.SYSTEM_PROMPT, RuntimeInstructionInjectionMode.valueOf("SYSTEM_PROMPT"));
        assertEquals(RuntimeInstructionInjectionMode.METADATA, RuntimeInstructionInjectionMode.valueOf("METADATA"));
    }
}
