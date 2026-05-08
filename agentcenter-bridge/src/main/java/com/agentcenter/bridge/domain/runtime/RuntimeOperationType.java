package com.agentcenter.bridge.domain.runtime;

public enum RuntimeOperationType {
    SKILL_INSTALL("skill.install"),
    SKILL_DELETE("skill.delete"),
    SKILL_RUN("skill.run"),
    SKILL_SCAN("skill.scan"),
    MCP_READ_CONFIG("mcp.read_config"),
    MCP_WRITE_CONFIG("mcp.write_config"),
    MCP_REFRESH("mcp.refresh"),
    RUNTIME_HEALTH_CHECK("runtime.health_check");

    private final String value;

    RuntimeOperationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RuntimeOperationType fromValue(String value) {
        for (var t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown operation type: " + value);
    }
}
