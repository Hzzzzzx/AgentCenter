package com.agentcenter.bridge.api.dto;

public record ProjectContextDto(
        String id,
        String externalProjectId,
        String project,
        String externalCloudeReqProjectId,
        String cloudeReqProject,
        String externalSpaceId,
        String space,
        String externalIterationId,
        String iteration,
        String iterationStatus,
        String iterationStartAt,
        String iterationEndAt,
        boolean active,
        String extraJson
) {
    public ProjectContextDto(
            String id,
            String project,
            String cloudeReqProject,
            String space,
            String iteration,
            boolean active
    ) {
        this(
                id,
                project,
                project,
                cloudeReqProject,
                cloudeReqProject,
                space,
                space,
                iteration,
                iteration,
                null,
                null,
                null,
                active,
                null
        );
    }
}
