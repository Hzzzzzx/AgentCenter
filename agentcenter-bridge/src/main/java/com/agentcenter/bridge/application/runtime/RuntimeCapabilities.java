package com.agentcenter.bridge.application.runtime;

/**
 * Declares what a runtime provider supports.
 */
public record RuntimeCapabilities(
    boolean conversationStreaming,
    boolean skillLifecycle,
    boolean mcpLifecycle,
    boolean cancelSupported
) {}
