package com.agentcenter.bridge.application.runtime.translation;

import java.util.List;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;

public interface RuntimeEventTranslator {
    List<RuntimeEventEnvelope> translate(RuntimeRawEvent raw, RuntimeTranslationContext context);
}
