package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataProviderDto;
import com.agentcenter.bridge.api.dto.ProjectDataProviderSettingsDto;
import com.agentcenter.bridge.api.dto.ProjectDataScopeSelectionDto;
import com.agentcenter.bridge.api.dto.UpdateProjectDataScopeRequest;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectContextEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectIterationEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectProviderSettingEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectSpaceEntity;
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
        ActiveExternalScope activeExternalScope = activeExternalScopeFor(setting, activeProviderId);
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
                setting.getActiveProjectIterationId(),
                activeExternalScope.projectName(),
                activeExternalScope.externalProjectId(),
                activeExternalScope.externalSpaceId(),
                activeExternalScope.externalIterationId()
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
        clearExternalScope(setting);
        setting.setUpdatedAt(now());
        projectContextMapper.updateSetting(setting);
        return getSettings();
    }

    @Transactional
    public ProjectDataProviderSettingsDto setActiveScope(UpdateProjectDataScopeRequest request) {
        String providerId = clean(request.providerId());
        if (providerId == null) {
            providerId = ensureSetting().getActiveProviderId();
        }
        try {
            registry.require(providerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

        String externalProjectId = stripProviderPrefix(
                firstNonBlank(request.externalProjectId(), request.projectId()),
                providerId
        );
        String externalSpaceId = firstNonBlank(request.externalSpaceId(), request.spaceId());
        String externalIterationId = firstNonBlank(request.externalIterationId(), request.iterationId());
        String projectName = firstNonBlank(request.projectName(), externalProjectId);
        if (externalProjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalProjectId is required");
        }
        if (externalSpaceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalSpaceId is required");
        }
        if (externalIterationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalIterationId is required");
        }

        ProjectContextEntity context = projectContextMapper.findContextByProviderAndExternalProjectId(
                providerId,
                externalProjectId
        );
        ProjectSpaceEntity space = context == null
                ? null
                : projectContextMapper.findSpaceByProviderAndExternalSpaceId(
                        providerId,
                        context.getId(),
                        externalSpaceId
                );
        ProjectIterationEntity iteration = space == null
                ? null
                : projectContextMapper.findIterationByProviderAndExternalIterationId(
                        providerId,
                        space.getId(),
                        externalIterationId
                );
        if ((context != null && space == null) || (space != null && iteration == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SCOPE_INVALID: saved IDs do not match synced project hierarchy"
            );
        }

        var setting = ensureSetting();
        setting.setActiveProviderId(providerId);
        setting.setActiveProjectContextId(context != null ? context.getId() : null);
        setting.setActiveProjectSpaceId(space != null ? space.getId() : null);
        setting.setActiveProjectIterationId(iteration != null ? iteration.getId() : null);
        setting.setActiveProjectName(projectName);
        setting.setActiveExternalProjectId(externalProjectId);
        setting.setActiveExternalSpaceId(externalSpaceId);
        setting.setActiveExternalIterationId(externalIterationId);
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
        applyExternalScopeFromInternalIds(setting, projectContextId, projectSpaceId, projectIterationId);
        setting.setUpdatedAt(now());
        projectContextMapper.updateSetting(setting);
    }

    public ActiveScopeIds activeScopeIds(String providerId) {
        var setting = ensureSetting();
        if (!setting.getActiveProviderId().equals(providerId)) {
            return new ActiveScopeIds(null, null, null);
        }
        return new ActiveScopeIds(
                setting.getActiveProjectContextId(),
                setting.getActiveProjectSpaceId(),
                setting.getActiveProjectIterationId()
        );
    }

    public ProjectDataScopeSelectionDto activeScopeSelection(String providerId) {
        var setting = ensureSetting();
        if (!setting.getActiveProviderId().equals(providerId)) {
            return new ProjectDataScopeSelectionDto(providerId, null, null, null, null);
        }
        ActiveExternalScope externalScope = activeExternalScopeFor(setting, providerId);
        return new ProjectDataScopeSelectionDto(
                providerId,
                externalScope.projectName(),
                externalScope.externalProjectId(),
                externalScope.externalSpaceId(),
                externalScope.externalIterationId()
        );
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
            clearExternalScope(setting);
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

    private ActiveExternalScope activeExternalScopeFor(ProjectProviderSettingEntity setting, String activeProviderId) {
        if (setting.getActiveExternalProjectId() != null || setting.getActiveExternalSpaceId() != null) {
            return new ActiveExternalScope(
                    setting.getActiveProjectName(),
                    setting.getActiveExternalProjectId(),
                    setting.getActiveExternalSpaceId(),
                    setting.getActiveExternalIterationId()
            );
        }
        ProjectContextEntity context = projectContextMapper.findContextById(setting.getActiveProjectContextId());
        if (context == null || !activeProviderId.equals(context.getProviderId())) {
            return new ActiveExternalScope(setting.getActiveProjectName(), null, null, null);
        }
        ProjectSpaceEntity space = projectContextMapper.findSpaceById(setting.getActiveProjectSpaceId());
        String externalSpaceId = space != null && activeProviderId.equals(space.getProviderId())
                ? space.getExternalSpaceId()
                : null;
        ProjectIterationEntity iteration = projectContextMapper.findIterationById(setting.getActiveProjectIterationId());
        String externalIterationId = iteration != null && activeProviderId.equals(iteration.getProviderId())
                ? iteration.getExternalIterationId()
                : null;
        return new ActiveExternalScope(
                setting.getActiveProjectName() != null ? setting.getActiveProjectName() : context.getProjectName(),
                context.getExternalProjectId(),
                externalSpaceId,
                externalIterationId
        );
    }

    private void clearExternalScope(ProjectProviderSettingEntity setting) {
        setting.setActiveProjectName(null);
        setting.setActiveExternalProjectId(null);
        setting.setActiveExternalSpaceId(null);
        setting.setActiveExternalIterationId(null);
    }

    private void applyExternalScopeFromInternalIds(ProjectProviderSettingEntity setting,
                                                   String projectContextId,
                                                   String projectSpaceId,
                                                   String projectIterationId) {
        ProjectContextEntity context = projectContextMapper.findContextById(projectContextId);
        ProjectSpaceEntity space = projectContextMapper.findSpaceById(projectSpaceId);
        ProjectIterationEntity iteration = projectIterationId == null
                ? null
                : projectContextMapper.findIterationById(projectIterationId);
        if (context == null || space == null) {
            clearExternalScope(setting);
            return;
        }
        String projectName = context.getExternalProjectId().equals(setting.getActiveExternalProjectId())
                ? firstNonBlank(setting.getActiveProjectName(), context.getProjectName())
                : context.getProjectName();
        setting.setActiveProjectName(projectName);
        setting.setActiveExternalProjectId(context.getExternalProjectId());
        setting.setActiveExternalSpaceId(space.getExternalSpaceId());
        setting.setActiveExternalIterationId(iteration != null ? iteration.getExternalIterationId() : null);
    }

    private String firstNonBlank(String primary, String fallback) {
        String cleanPrimary = clean(primary);
        return cleanPrimary != null ? cleanPrimary : clean(fallback);
    }

    private String stripProviderPrefix(String value, String providerId) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        String prefix = providerId + ":";
        return cleaned.startsWith(prefix) ? cleaned.substring(prefix.length()) : cleaned;
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATETIME);
    }

    public record ActiveScopeIds(
            String projectContextId,
            String projectSpaceId,
            String projectIterationId
    ) {}

    private record ActiveExternalScope(
            String projectName,
            String externalProjectId,
            String externalSpaceId,
            String externalIterationId
    ) {}
}
