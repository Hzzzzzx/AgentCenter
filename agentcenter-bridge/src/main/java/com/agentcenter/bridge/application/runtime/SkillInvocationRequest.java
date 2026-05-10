package com.agentcenter.bridge.application.runtime;

public record SkillInvocationRequest(
    String skillName,
    String userPrompt,
    String instructionPrompt,
    RuntimeInstructionInjectionMode injectionMode
) {
    public static SkillInvocationRequest userPromptInjection(String skillName, String userPrompt, String instructionPrompt) {
        return new SkillInvocationRequest(skillName, userPrompt, instructionPrompt, RuntimeInstructionInjectionMode.USER_PROMPT);
    }
}
