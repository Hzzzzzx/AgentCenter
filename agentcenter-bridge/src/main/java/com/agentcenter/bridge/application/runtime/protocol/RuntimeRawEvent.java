package com.agentcenter.bridge.application.runtime.protocol;

import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeRawEvent(
    RuntimeType runtimeType,
    String rawType,
    JsonNode rawJson,
    String runtimeSessionId
) {}
