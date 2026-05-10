package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkillInvocationRequestTest {

    @Test
    void userPromptInjectionCreatesCorrectRequest() {
        SkillInvocationRequest request = SkillInvocationRequest.userPromptInjection("skill1", "user prompt", "instruction");

        assertEquals("skill1", request.skillName());
        assertEquals("user prompt", request.userPrompt());
        assertEquals("instruction", request.instructionPrompt());
        assertEquals(RuntimeInstructionInjectionMode.USER_PROMPT, request.injectionMode());
    }

    @Test
    void constructorAllowsAllInjectionModes() {
        for (RuntimeInstructionInjectionMode mode : RuntimeInstructionInjectionMode.values()) {
            SkillInvocationRequest request = new SkillInvocationRequest("skill", "prompt", "instruction", mode);
            assertEquals(mode, request.injectionMode());
        }
    }

    @Test
    void recordEquality() {
        SkillInvocationRequest a = new SkillInvocationRequest("s", "p", "i", RuntimeInstructionInjectionMode.SYSTEM_PROMPT);
        SkillInvocationRequest b = new SkillInvocationRequest("s", "p", "i", RuntimeInstructionInjectionMode.SYSTEM_PROMPT);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
