package com.agentcenter.bridge.application.runtime;

/**
 * Declares what a runtime provider supports.
 */
public record RuntimeCapabilities(
    boolean conversationStreaming,
    boolean skillLifecycle,
    boolean mcpLifecycle,
    boolean cancelSupported,
    String commandTransport,
    String eventTransport,
    String resourceMutationMode,
    boolean supportsAsyncOperations
) {
    public static final String HTTP = "HTTP";
    public static final String WEBSOCKET = "WEBSOCKET";
    public static final String SSE = "SSE";
    public static final String LOCAL_FILE = "LOCAL_FILE";
    public static final String REMOTE_COMMAND = "REMOTE_COMMAND";
}
