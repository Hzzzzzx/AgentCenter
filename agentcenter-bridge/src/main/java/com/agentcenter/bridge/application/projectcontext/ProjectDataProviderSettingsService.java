package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataProviderDto;
import com.agentcenter.bridge.api.dto.ProjectDataProviderSettingsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectDataProviderSettingsService {

    private final ProjectDataProviderRegistry registry;
    private volatile String activeProviderId;

    public ProjectDataProviderSettingsService(
            ProjectDataProviderRegistry registry,
            @Value("${agentcenter.project-context.provider:}") String configuredProviderId
    ) {
        this.registry = registry;
        this.activeProviderId = configuredProviderId == null || configuredProviderId.isBlank()
                ? registry.defaultProviderId()
                : configuredProviderId.trim();
        registry.require(this.activeProviderId);
    }

    public ProjectDataProviderSettingsDto getSettings() {
        var providers = registry.providers().stream()
                .map(provider -> new ProjectDataProviderDto(
                        provider.id(),
                        provider.name(),
                        provider.description(),
                        provider.id().equals(activeProviderId)
                ))
                .toList();
        return new ProjectDataProviderSettingsDto(providers, activeProviderId);
    }

    public ProjectDataProviderSettingsDto setActiveProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        try {
            registry.require(providerId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        activeProviderId = providerId.trim();
        return getSettings();
    }

    public ProjectDataProvider activeProvider() {
        return registry.require(activeProviderId);
    }
}
