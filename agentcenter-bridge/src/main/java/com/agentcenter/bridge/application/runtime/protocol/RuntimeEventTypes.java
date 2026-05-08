package com.agentcenter.bridge.application.runtime.protocol;

public final class RuntimeEventTypes {
    private RuntimeEventTypes() {}
    public static final String CONVERSATION_DELTA = "conversation.delta";
    public static final String CONVERSATION_COMPLETED = "conversation.completed";
    public static final String TOOL_STARTED = "tool.started";
    public static final String TOOL_COMPLETED = "tool.completed";
    public static final String PERMISSION_REQUESTED = "permission.requested";
    public static final String RUNTIME_STATUS_CHANGED = "runtime.status.changed";
    public static final String RUNTIME_ERROR = "runtime.error";
}
