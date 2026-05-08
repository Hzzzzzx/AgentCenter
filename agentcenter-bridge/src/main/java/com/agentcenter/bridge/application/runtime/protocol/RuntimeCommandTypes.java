package com.agentcenter.bridge.application.runtime.protocol;

public final class RuntimeCommandTypes {
    private RuntimeCommandTypes() {}
    public static final String SESSION_ENSURE = "session.ensure";
    public static final String CONVERSATION_MESSAGE_SEND = "conversation.message.send";
    public static final String CONVERSATION_CANCEL = "conversation.cancel";
    public static final String SKILL_RUN = "skill.run";
    public static final String SKILL_INSTALL = "skill.install";
    public static final String SKILL_DELETE = "skill.delete";
    public static final String SKILL_SCAN = "skill.scan";
    public static final String MCP_READ_CONFIG = "mcp.read_config";
    public static final String MCP_WRITE_CONFIG = "mcp.write_config";
    public static final String MCP_REFRESH = "mcp.refresh";
    public static final String PERMISSION_RESPOND = "permission.respond";
    public static final String RUNTIME_HEALTH_CHECK = "runtime.health_check";
}
