package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectProviderWorkItemDto;
import com.agentcenter.bridge.api.dto.ProjectContextDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectContextEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectIterationEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectSpaceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectContextMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProjectDataSyncService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectDataProviderSettingsService settingsService;
    private final WorkItemMapper workItemMapper;
    private final ProjectContextMapper projectContextMapper;
    private final IdGenerator idGenerator;

    public ProjectDataSyncService(ProjectDataProviderSettingsService settingsService,
                                  WorkItemMapper workItemMapper,
                                  ProjectContextMapper projectContextMapper,
                                  IdGenerator idGenerator) {
        this.settingsService = settingsService;
        this.workItemMapper = workItemMapper;
        this.projectContextMapper = projectContextMapper;
        this.idGenerator = idGenerator;
    }

    public ProjectDataSnapshotDto snapshot() {
        return settingsService.activeProvider().snapshot();
    }

    @Transactional
    public ProjectDataSnapshotDto sync() {
        ProjectDataSnapshotDto snapshot = snapshot();
        String providerId = snapshot.providerId();
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        Map<String, ProjectContextDto> contextByProviderContextId = new HashMap<>();
        SyncedScope activeScope = null;

        projectContextMapper.clearActiveContexts(providerId);
        for (ProjectContextDto context : snapshot.contexts()) {
            SyncedScope scope = upsertScopeFromContext(providerId, context, now);
            if (!isBlank(context.id())) {
                contextByProviderContextId.put(context.id(), context);
            }
            if (context.active() && activeScope == null) {
                activeScope = scope;
            }
        }

        for (ProjectProviderWorkItemDto item : snapshot.workItems()) {
            SyncedScope scope = upsertScopeFromWorkItem(providerId, item, contextByProviderContextId, now);
            if (activeScope == null) {
                activeScope = scope;
            }
            upsertWorkItem(providerId, item, scope, now);
        }

        if (activeScope != null) {
            settingsService.setActiveScope(
                    activeScope.context().getId(),
                    activeScope.space().getId(),
                    activeScope.iteration().getId()
            );
        }
        return snapshot;
    }

    private SyncedScope upsertScopeFromContext(String providerId, ProjectContextDto context, String now) {
        return upsertScope(
                providerId,
                context.externalProjectId(),
                context.project(),
                context.externalCloudeReqProjectId(),
                context.cloudeReqProject(),
                context.externalSpaceId(),
                context.space(),
                context.externalIterationId(),
                context.iteration(),
                context.iterationStatus(),
                context.iterationStartAt(),
                context.iterationEndAt(),
                context.active(),
                context.extraJson(),
                now
        );
    }

    private SyncedScope upsertScopeFromWorkItem(String providerId,
                                                ProjectProviderWorkItemDto item,
                                                Map<String, ProjectContextDto> contextByProviderContextId,
                                                String now) {
        ProjectContextDto context = isBlank(item.projectContextId())
                ? null
                : contextByProviderContextId.get(item.projectContextId());
        return upsertScope(
                providerId,
                firstNonBlank(item.externalProjectId(), context != null ? context.externalProjectId() : null),
                firstNonBlank(item.project(), context != null ? context.project() : null),
                context != null ? context.externalCloudeReqProjectId() : null,
                context != null ? context.cloudeReqProject() : null,
                firstNonBlank(item.externalSpaceId(), context != null ? context.externalSpaceId() : null),
                firstNonBlank(item.space(), context != null ? context.space() : null),
                firstNonBlank(item.externalIterationId(), context != null ? context.externalIterationId() : null),
                firstNonBlank(item.iteration(), context != null ? context.iteration() : null),
                context != null ? context.iterationStatus() : null,
                context != null ? context.iterationStartAt() : null,
                context != null ? context.iterationEndAt() : null,
                false,
                null,
                now
        );
    }

    private SyncedScope upsertScope(String providerId,
                                    String externalProjectId,
                                    String projectName,
                                    String externalCloudeReqProjectId,
                                    String cloudeReqProjectName,
                                    String externalSpaceId,
                                    String spaceName,
                                    String externalIterationId,
                                    String iterationName,
                                    String iterationStatus,
                                    String iterationStartAt,
                                    String iterationEndAt,
                                    boolean active,
                                    String extraJson,
                                    String now) {
        String projectDisplayName = requireNonBlank(firstNonBlank(projectName, externalProjectId), "project");
        String projectExternalId = requireNonBlank(firstNonBlank(externalProjectId, projectDisplayName), "externalProjectId");
        String spaceDisplayName = requireNonBlank(firstNonBlank(spaceName, externalSpaceId), "space");
        String spaceExternalId = requireNonBlank(firstNonBlank(externalSpaceId, spaceDisplayName), "externalSpaceId");
        String iterationDisplayName = requireNonBlank(firstNonBlank(iterationName, externalIterationId), "iteration");
        String iterationExternalId = requireNonBlank(firstNonBlank(externalIterationId, iterationDisplayName), "externalIterationId");

        ProjectContextEntity context = upsertContext(
                providerId,
                projectExternalId,
                projectDisplayName,
                clean(externalCloudeReqProjectId),
                clean(cloudeReqProjectName),
                active,
                extraJson,
                now
        );
        ProjectSpaceEntity space = upsertSpace(
                providerId,
                context.getId(),
                spaceExternalId,
                spaceDisplayName,
                null,
                now
        );
        ProjectIterationEntity iteration = upsertIteration(
                providerId,
                context.getId(),
                space.getId(),
                iterationExternalId,
                iterationDisplayName,
                clean(iterationStatus),
                clean(iterationStartAt),
                clean(iterationEndAt),
                null,
                now
        );
        return new SyncedScope(context, space, iteration);
    }

    private ProjectContextEntity upsertContext(String providerId,
                                               String externalProjectId,
                                               String projectName,
                                               String externalCloudeReqProjectId,
                                               String cloudeReqProjectName,
                                               boolean active,
                                               String extraJson,
                                               String now) {
        ProjectContextEntity existing = projectContextMapper.findContextByProviderAndExternalProjectId(
                providerId,
                externalProjectId
        );
        if (existing == null) {
            ProjectContextEntity entity = new ProjectContextEntity();
            entity.setId(idGenerator.nextId());
            entity.setProviderId(providerId);
            entity.setExternalProjectId(externalProjectId);
            entity.setProjectName(projectName);
            entity.setExternalCloudeReqProjectId(externalCloudeReqProjectId);
            entity.setCloudeReqProjectName(cloudeReqProjectName);
            entity.setActive(active ? 1 : 0);
            entity.setExtraJson(extraJson);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            projectContextMapper.insertContext(entity);
            return entity;
        }

        existing.setProjectName(projectName);
        existing.setExternalCloudeReqProjectId(externalCloudeReqProjectId);
        existing.setCloudeReqProjectName(cloudeReqProjectName);
        existing.setActive(active ? 1 : existing.getActive());
        existing.setExtraJson(extraJson);
        existing.setUpdatedAt(now);
        projectContextMapper.updateContext(existing);
        return existing;
    }

    private ProjectSpaceEntity upsertSpace(String providerId,
                                           String projectContextId,
                                           String externalSpaceId,
                                           String spaceName,
                                           String extraJson,
                                           String now) {
        ProjectSpaceEntity existing = projectContextMapper.findSpaceByProviderAndExternalSpaceId(
                providerId,
                projectContextId,
                externalSpaceId
        );
        if (existing == null) {
            ProjectSpaceEntity entity = new ProjectSpaceEntity();
            entity.setId(idGenerator.nextId());
            entity.setProviderId(providerId);
            entity.setProjectContextId(projectContextId);
            entity.setExternalSpaceId(externalSpaceId);
            entity.setSpaceName(spaceName);
            entity.setExtraJson(extraJson);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            projectContextMapper.insertSpace(entity);
            return entity;
        }

        existing.setSpaceName(spaceName);
        existing.setExtraJson(extraJson);
        existing.setUpdatedAt(now);
        projectContextMapper.updateSpace(existing);
        return existing;
    }

    private ProjectIterationEntity upsertIteration(String providerId,
                                                   String projectContextId,
                                                   String projectSpaceId,
                                                   String externalIterationId,
                                                   String iterationName,
                                                   String status,
                                                   String startAt,
                                                   String endAt,
                                                   String extraJson,
                                                   String now) {
        ProjectIterationEntity existing = projectContextMapper.findIterationByProviderAndExternalIterationId(
                providerId,
                projectSpaceId,
                externalIterationId
        );
        if (existing == null) {
            ProjectIterationEntity entity = new ProjectIterationEntity();
            entity.setId(idGenerator.nextId());
            entity.setProviderId(providerId);
            entity.setProjectContextId(projectContextId);
            entity.setProjectSpaceId(projectSpaceId);
            entity.setExternalIterationId(externalIterationId);
            entity.setIterationName(iterationName);
            entity.setStatus(status);
            entity.setStartAt(startAt);
            entity.setEndAt(endAt);
            entity.setExtraJson(extraJson);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            projectContextMapper.insertIteration(entity);
            return entity;
        }

        existing.setIterationName(iterationName);
        existing.setStatus(status);
        existing.setStartAt(startAt);
        existing.setEndAt(endAt);
        existing.setExtraJson(extraJson);
        existing.setUpdatedAt(now);
        projectContextMapper.updateIteration(existing);
        return existing;
    }

    private void upsertWorkItem(String providerId, ProjectProviderWorkItemDto item, SyncedScope scope, String now) {
        String externalWorkItemId = requireNonBlank(firstNonBlank(item.externalId(), item.code()), "externalId");
        WorkItemEntity existing = workItemMapper.findByProviderAndExternalId(providerId, externalWorkItemId);
        if (existing == null) {
            WorkItemEntity byCode = workItemMapper.findByCode(item.code());
            if (byCode != null && (isBlank(byCode.getProviderId()) || providerId.equals(byCode.getProviderId()))) {
                existing = byCode;
            } else if (byCode != null) {
                throw new IllegalStateException("Work item code already belongs to another provider: " + item.code());
            }
        }

        if (existing == null) {
            WorkItemEntity entity = new WorkItemEntity();
            entity.setId(idGenerator.nextId());
            applyWorkItemSyncFields(providerId, externalWorkItemId, item, scope, entity);
            entity.setVersion(1);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            workItemMapper.insert(entity);
            return;
        }

        applyWorkItemSyncFields(providerId, externalWorkItemId, item, scope, existing);
        existing.setUpdatedAt(now);
        workItemMapper.updateFromSync(existing);
    }

    private void applyWorkItemSyncFields(String providerId,
                                         String externalWorkItemId,
                                         ProjectProviderWorkItemDto item,
                                         SyncedScope scope,
                                         WorkItemEntity entity) {
        entity.setCode(requireNonBlank(item.code(), "code"));
        entity.setType(item.type().name());
        entity.setTitle(requireNonBlank(item.title(), "title"));
        entity.setDescription(item.description());
        entity.setStatus(item.status().name());
        entity.setPriority(item.priority().name());
        entity.setProviderId(providerId);
        entity.setExternalWorkItemId(externalWorkItemId);
        entity.setProjectId(scope.context().getProjectName());
        entity.setSpaceId(scope.space().getSpaceName());
        entity.setIterationId(scope.iteration().getIterationName());
        entity.setProjectContextId(scope.context().getId());
        entity.setProjectSpaceId(scope.space().getId());
        entity.setProjectIterationId(scope.iteration().getId());
        entity.setAssigneeUserId(item.assigneeUserId());
        entity.setExtraJson(item.extraJson());
    }

    private String firstNonBlank(String primary, String fallback) {
        return !isBlank(primary) ? primary : fallback;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Project data provider returned blank " + fieldName);
        }
        return value.trim();
    }

    private String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SyncedScope(
            ProjectContextEntity context,
            ProjectSpaceEntity space,
            ProjectIterationEntity iteration
    ) {}
}
