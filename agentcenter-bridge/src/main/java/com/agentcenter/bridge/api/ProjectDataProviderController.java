package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.ProjectDataProviderSettingsDto;
import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.UpdateProjectDataProviderRequest;
import com.agentcenter.bridge.application.projectcontext.ProjectDataProviderSettingsService;
import com.agentcenter.bridge.application.projectcontext.ProjectDataSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-data-providers")
public class ProjectDataProviderController {

    private final ProjectDataProviderSettingsService settingsService;
    private final ProjectDataSyncService syncService;

    public ProjectDataProviderController(ProjectDataProviderSettingsService settingsService,
                                         ProjectDataSyncService syncService) {
        this.settingsService = settingsService;
        this.syncService = syncService;
    }

    @GetMapping
    public ProjectDataProviderSettingsDto settings() {
        return settingsService.getSettings();
    }

    @PutMapping("/active")
    public ProjectDataProviderSettingsDto setActive(@RequestBody UpdateProjectDataProviderRequest request) {
        return settingsService.setActiveProvider(request.providerId());
    }

    @GetMapping("/snapshot")
    public ProjectDataSnapshotDto snapshot() {
        return syncService.snapshot();
    }

    @PostMapping("/sync")
    public ProjectDataSnapshotDto sync() {
        return syncService.sync();
    }
}
