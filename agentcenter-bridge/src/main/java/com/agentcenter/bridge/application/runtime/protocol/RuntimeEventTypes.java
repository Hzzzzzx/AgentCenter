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
    public static final String CONVERSATION_FAILED = "conversation.failed";
    public static final String TOOL_FAILED = "tool.failed";
    public static final String SKILL_INSTALL_COMPLETED = "skill.install.completed";
    public static final String SKILL_INSTALL_FAILED = "skill.install.failed";
    public static final String SKILL_DELETE_COMPLETED = "skill.delete.completed";
    public static final String SKILL_DELETE_FAILED = "skill.delete.failed";
    public static final String SKILL_RUN_STARTED = "skill.run.started";
    public static final String SKILL_RUN_COMPLETED = "skill.run.completed";
    public static final String SKILL_RUN_FAILED = "skill.run.failed";
    public static final String MCP_CONFIG_UPDATED = "mcp.config.updated";
    public static final String MCP_REFRESH_COMPLETED = "mcp.refresh.completed";
    public static final String MCP_REFRESH_FAILED = "mcp.refresh.failed";
}
