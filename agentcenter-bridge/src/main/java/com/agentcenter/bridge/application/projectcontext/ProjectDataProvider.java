package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;

public interface ProjectDataProvider {
    String id();

    String name();

    String description();

    ProjectDataSnapshotDto snapshot();
}
