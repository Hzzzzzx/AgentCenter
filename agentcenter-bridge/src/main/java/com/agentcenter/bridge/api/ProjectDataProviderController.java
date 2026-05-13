package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.ProjectDataProviderSettingsDto;
import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectDataSyncHistoryDto;
import com.agentcenter.bridge.api.dto.UpdateProjectDataProviderRequest;
import com.agentcenter.bridge.api.dto.UpdateProjectDataScopeRequest;
import com.agentcenter.bridge.application.projectcontext.ProjectDataProviderSettingsService;
import com.agentcenter.bridge.application.projectcontext.ProjectDataSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PutMapping("/active-scope")
    public ProjectDataProviderSettingsDto setActiveScope(@RequestBody UpdateProjectDataScopeRequest request) {
        return settingsService.setActiveScope(request);
    }

    @GetMapping("/snapshot")
    public ProjectDataSnapshotDto snapshot() {
        return syncService.snapshot();
    }

    @PostMapping("/sync")
    public ProjectDataSnapshotDto sync() {
        return syncService.sync();
    }

    @PostMapping("/select-and-sync")
    public ProjectDataProviderSettingsDto selectAndSync(@RequestBody UpdateProjectDataScopeRequest request) {
        syncService.sync();
        return settingsService.setActiveScope(request);
    }

    @GetMapping("/sync-history")
    public List<ProjectDataSyncHistoryDto> syncHistory(@RequestParam(required = false) String providerId,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return syncService.listHistory(providerId, limit);
    }
}
