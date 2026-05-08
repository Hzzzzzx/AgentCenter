package com.agentcenter.bridge.application.runtime;

/**
 * Describes a runtime provider's identity and transport.
 */
public record RuntimeDescriptor(
    String displayName,
    String transportType,
    String description,
    RuntimeCapabilities capabilities
) {}
