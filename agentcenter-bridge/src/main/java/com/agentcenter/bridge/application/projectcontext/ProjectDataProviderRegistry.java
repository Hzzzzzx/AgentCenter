package com.agentcenter.bridge.application.projectcontext;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProjectDataProviderRegistry {

    private final Map<String, ProjectDataProvider> providersById;
    private final List<ProjectDataProvider> providers;

    public ProjectDataProviderRegistry(List<ProjectDataProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(ProjectDataProvider::id))
                .toList();
        Map<String, ProjectDataProvider> indexed = new LinkedHashMap<>();
        for (ProjectDataProvider provider : this.providers) {
            if (indexed.put(provider.id(), provider) != null) {
                throw new IllegalStateException("Duplicate project data provider id: " + provider.id());
            }
        }
        this.providersById = Map.copyOf(indexed);
    }

    public List<ProjectDataProvider> providers() {
        return providers;
    }

    public ProjectDataProvider require(String providerId) {
        ProjectDataProvider provider = providersById.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown project data provider: " + providerId);
        }
        return provider;
    }

    public String defaultProviderId() {
        if (providers.isEmpty()) {
            throw new IllegalStateException("No project data providers registered");
        }
        return providers.get(0).id();
    }
}
