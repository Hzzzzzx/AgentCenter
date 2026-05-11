package com.agentcenter.bridge.application;

public final class ProjectDefaults {

    public static final String DEFAULT_PROJECT_ID = "01DEFAULTPROJECT0000000000001";

    private ProjectDefaults() {
    }

    public static String resolveProjectId(String projectId) {
        return projectId == null || projectId.isBlank() ? DEFAULT_PROJECT_ID : projectId.trim();
    }
}
