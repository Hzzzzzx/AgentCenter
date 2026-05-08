package com.agentcenter.bridge.application.runtime.protocol;

public record RuntimeAckEnvelope(
    String protocol,
    String type,
    boolean success,
    String correlationId,
    String message
) {}
