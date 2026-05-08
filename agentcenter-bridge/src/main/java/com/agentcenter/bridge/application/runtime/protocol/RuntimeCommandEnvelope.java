package com.agentcenter.bridge.application.runtime.protocol;

import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeCommandEnvelope(
    String protocol,
    String type,
    RuntimeType runtimeType,
    String sessionId,
    JsonNode payload
) {}
