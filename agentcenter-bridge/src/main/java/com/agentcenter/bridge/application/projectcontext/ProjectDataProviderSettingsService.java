package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataProviderDto;
import com.agentcenter.bridge.api.dto.ProjectDataProviderSettingsDto;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectProviderSettingEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectContextMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ProjectDataProviderSettingsService {

    private static final String GLOBAL_SETTING_ID = "global";
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectDataProviderRegistry registry;
    private final ProjectContextMapper projectContextMapper;
    private final String configuredProviderId;

    public ProjectDataProviderSettingsService(
            ProjectDataProviderRegistry registry,
            ProjectContextMapper projectContextMapper,
            @Value("${agentcenter.project-context.provider:}") String configuredProviderId
    ) {
        this.registry = registry;
        this.projectContextMapper = projectContextMapper;
        this.configuredProviderId = clean(configuredProviderId);
    }

    public ProjectDataProviderSettingsDto getSettings() {
        var setting = ensureSetting();
        String activeProviderId = setting.getActiveProviderId();
        var providers = registry.providers().stream()
                .map(provider -> new ProjectDataProviderDto(
                        provider.id(),
                        provider.name(),
                        provider.description(),
                        provider.id().equals(activeProviderId)
                ))
                .toList();
        return new ProjectDataProviderSettingsDto(
                providers,
                activeProviderId,
                setting.getActiveProjectContextId(),
                setting.getActiveProjectSpaceId(),
                setting.getActiveProjectIterationId()
        );
    }

    @Transactional
    public ProjectDataProviderSettingsDto setActiveProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        String nextProviderId = providerId.trim();
        try {
            registry.require(nextProviderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        var setting = ensureSetting();
        setting.setActiveProviderId(nextProviderId);
        setting.setActiveProjectContextId(null);
        setting.setActiveProjectSpaceId(null);
        setting.setActiveProjectIterationId(null);
        setting.setUpdatedAt(now());
        projectContextMapper.updateSetting(setting);
        return getSettings();
    }

    public ProjectDataProvider activeProvider() {
        return registry.require(ensureSetting().getActiveProviderId());
    }

    @Transactional
    public void setActiveScope(String projectContextId, String projectSpaceId, String projectIterationId) {
        var setting = ensureSetting();
        setting.setActiveProjectContextId(projectContextId);
        setting.setActiveProjectSpaceId(projectSpaceId);
        setting.setActiveProjectIterationId(projectIterationId);
        setting.setUpdatedAt(now());
        projectContextMapper.updateSetting(setting);
    }

    private ProjectProviderSettingEntity ensureSetting() {
        var setting = projectContextMapper.findSetting(GLOBAL_SETTING_ID);
        String defaultProviderId = defaultProviderId();
        if (setting == null) {
            setting = new ProjectProviderSettingEntity();
            setting.setId(GLOBAL_SETTING_ID);
            setting.setActiveProviderId(defaultProviderId);
            setting.setUpdatedAt(now());
            projectContextMapper.insertSetting(setting);
            return setting;
        }
        try {
            registry.require(setting.getActiveProviderId());
            return setting;
        } catch (IllegalArgumentException e) {
            setting.setActiveProviderId(defaultProviderId);
            setting.setActiveProjectContextId(null);
            setting.setActiveProjectSpaceId(null);
            setting.setActiveProjectIterationId(null);
            setting.setUpdatedAt(now());
            projectContextMapper.updateSetting(setting);
            return setting;
        }
    }

    private String defaultProviderId() {
        String defaultProviderId = configuredProviderId == null
                ? registry.defaultProviderId()
                : configuredProviderId;
        registry.require(defaultProviderId);
        return defaultProviderId;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATETIME);
    }
}
