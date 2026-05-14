package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectDataScopeSelectionDto;

public interface ProjectDataProvider {
    String id();

    String name();

    String description();

    ProjectDataSnapshotDto snapshot();

    default ProjectDataSnapshotDto snapshot(ProjectDataScopeSelectionDto selection) {
        return snapshot();
    }
}
