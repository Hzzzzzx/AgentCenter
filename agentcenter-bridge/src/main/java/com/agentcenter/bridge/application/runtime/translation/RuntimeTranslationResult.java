package com.agentcenter.bridge.application.runtime.translation;

import java.util.List;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;

public record RuntimeTranslationResult(
    List<RuntimeEventEnvelope> envelopes
) {
    public static RuntimeTranslationResult empty() {
        return new RuntimeTranslationResult(List.of());
    }

    public static RuntimeTranslationResult of(RuntimeEventEnvelope... envelopes) {
        return new RuntimeTranslationResult(List.of(envelopes));
    }

    public static RuntimeTranslationResult of(List<RuntimeEventEnvelope> envelopes) {
        return new RuntimeTranslationResult(envelopes);
    }
}
