package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectDataScopeSelectionDto;
import com.agentcenter.bridge.api.dto.ProjectDataSyncHistoryDto;
import com.agentcenter.bridge.api.dto.ProjectDataSyncStatsDto;
import com.agentcenter.bridge.api.dto.ProjectProviderWorkItemDto;
import com.agentcenter.bridge.api.dto.ProjectContextDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectContextEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectDataSyncHistoryEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectIterationEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectSpaceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectContextMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectDataSyncService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectDataProviderSettingsService settingsService;
    private final WorkItemMapper workItemMapper;
    private final ProjectContextMapper projectContextMapper;
    private final ProjectDataSyncHistoryService historyService;
    private final ProjectWorkflowProvisioningService workflowProvisioningService;
    private final TransactionTemplate transactionTemplate;
    private final IdGenerator idGenerator;

    public ProjectDataSyncService(ProjectDataProviderSettingsService settingsService,
                                  WorkItemMapper workItemMapper,
                                  ProjectContextMapper projectContextMapper,
                                  ProjectDataSyncHistoryService historyService,
                                  ProjectWorkflowProvisioningService workflowProvisioningService,
                                  TransactionTemplate transactionTemplate,
                                  IdGenerator idGenerator) {
        this.settingsService = settingsService;
        this.workItemMapper = workItemMapper;
        this.projectContextMapper = projectContextMapper;
        this.historyService = historyService;
        this.workflowProvisioningService = workflowProvisioningService;
        this.transactionTemplate = transactionTemplate;
        this.idGenerator = idGenerator;
    }

    public ProjectDataSnapshotDto snapshot() {
        ProjectDataProvider provider = settingsService.activeProvider();
        return provider.snapshot(settingsService.activeScopeSelection(provider.id()));
    }

    public ProjectDataSnapshotDto sync() {
        ProjectDataProvider provider = settingsService.activeProvider();
        ProjectDataSyncHistoryEntity history = historyService.start(provider.id());
        try {
            ProjectDataScopeSelectionDto selection = settingsService.activeScopeSelection(provider.id());
            ProjectDataSnapshotDto snapshot = provider.snapshot(selection);
            if (!provider.id().equals(snapshot.providerId())) {
                throw new IllegalStateException("Project data provider returned mismatched providerId: "
                        + snapshot.providerId());
            }
            SyncResult result = transactionTemplate.execute(status -> syncSnapshot(snapshot));
            SyncedScope activeScope = result != null ? result.activeScope() : null;
            ProjectDataSyncStatsDto stats = result != null
                    ? result.stats()
                    : new ProjectDataSyncStatsDto(snapshot.workItems().size(), 0, 0, 0, 0);
            historyService.markSuccess(
                    history,
                    snapshot.contexts().size(),
                    snapshot.workItems().size(),
                    activeScope != null ? activeScope.context().getId() : null,
                    activeScope != null ? activeScope.space().getId() : null,
                    activeScope != null && activeScope.iteration() != null ? activeScope.iteration().getId() : null,
                    syncResultJson(snapshot, activeScope)
            );
            return new ProjectDataSnapshotDto(
                    snapshot.providerId(),
                    snapshot.contexts(),
                    snapshot.options(),
                    snapshot.workItems(),
                    snapshot.syncedAt(),
                    stats
            );
        } catch (RuntimeException e) {
            historyService.markFailed(history, e.getMessage());
            throw e;
        }
    }

    private SyncResult syncSnapshot(ProjectDataSnapshotDto snapshot) {
        String providerId = snapshot.providerId();
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        Map<String, ProjectContextDto> contextByProviderContextId = new HashMap<>();
        Set<String> syncedProjectIds = new LinkedHashSet<>();
        Set<String> syncedScopeKeys = new LinkedHashSet<>();
        SyncedScope providerActiveScope = null;
        SyncedScope firstScope = null;
        int created = 0;
        int updated = 0;

        projectContextMapper.clearActiveContexts(providerId);
        for (ProjectContextDto context : snapshot.contexts()) {
            SyncedScope scope = upsertScopeFromContext(providerId, context, now);
            syncedProjectIds.add(projectScopeId(providerId, scope.context().getExternalProjectId()));
            if (firstScope == null) {
                firstScope = scope;
            }
            syncedScopeKeys.add(scopeKey(providerId, scope));
            if (!isBlank(context.id())) {
                contextByProviderContextId.put(context.id(), context);
            }
            if (context.active() && providerActiveScope == null) {
                providerActiveScope = scope;
            }
        }

        for (ProjectProviderWorkItemDto item : snapshot.workItems()) {
            SyncedScope scope = upsertScopeFromWorkItem(providerId, item, contextByProviderContextId, now);
            syncedProjectIds.add(projectScopeId(providerId, scope.context().getExternalProjectId()));
            if (firstScope == null) {
                firstScope = scope;
            }
            syncedScopeKeys.add(scopeKey(providerId, scope));
            UpsertWorkItemResult upsertResult = upsertWorkItem(providerId, item, scope, now);
            if (upsertResult == UpsertWorkItemResult.CREATED) {
                created++;
            } else {
                updated++;
            }
        }

        workflowProvisioningService.ensureFeWorkflowForProjects(syncedProjectIds);

        SyncedScope activeScope = firstNonNull(
                resolveSavedScope(providerId, syncedScopeKeys),
                providerActiveScope,
                firstScope
        );
        if (activeScope != null) {
            settingsService.setActiveScope(
                    activeScope.context().getId(),
                    activeScope.space().getId(),
                    activeScope.iteration() != null ? activeScope.iteration().getId() : null
            );
        }
        ProjectDataSyncStatsDto stats = new ProjectDataSyncStatsDto(
                snapshot.workItems().size(),
                created,
                updated,
                0,
                0
        );
        return new SyncResult(activeScope, stats);
    }

    public List<ProjectDataSyncHistoryDto> listHistory(String providerId, int limit) {
        return historyService.list(providerId, limit);
    }

    private String syncResultJson(ProjectDataSnapshotDto snapshot, SyncedScope activeScope) {
        return "{"
                + "\"providerId\":\"" + snapshot.providerId() + "\","
                + "\"contextCount\":" + snapshot.contexts().size() + ","
                + "\"workItemCount\":" + snapshot.workItems().size() + ","
                + "\"activeProjectContextId\":"
                + quote(activeScope != null ? activeScope.context().getId() : null)
                + "}";
    }

    private String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
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

    private UpsertWorkItemResult upsertWorkItem(String providerId, ProjectProviderWorkItemDto item, SyncedScope scope, String now) {
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
            return UpsertWorkItemResult.CREATED;
        }

        applyWorkItemSyncFields(providerId, externalWorkItemId, item, scope, existing);
        existing.setUpdatedAt(now);
        workItemMapper.updateFromSync(existing);
        return UpsertWorkItemResult.UPDATED;
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
        entity.setProjectId(projectScopeId(providerId, scope.context().getExternalProjectId()));
        entity.setSpaceId(scope.space().getExternalSpaceId());
        entity.setIterationId(scope.iteration().getExternalIterationId());
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

    private String projectScopeId(String providerId, String externalProjectId) {
        return providerId + ":" + requireNonBlank(externalProjectId, "externalProjectId");
    }

    private SyncedScope resolveSavedScope(String providerId, Set<String> syncedScopeKeys) {
        SyncedScope externalScope = resolveSavedExternalScope(providerId, syncedScopeKeys);
        if (externalScope != null) {
            return externalScope;
        }
        ProjectDataProviderSettingsService.ActiveScopeIds ids = settingsService.activeScopeIds(providerId);
        if (ids.projectContextId() == null || ids.projectSpaceId() == null) {
            return null;
        }
        ProjectContextEntity context = projectContextMapper.findContextById(ids.projectContextId());
        ProjectSpaceEntity space = projectContextMapper.findSpaceById(ids.projectSpaceId());
        ProjectIterationEntity iteration = ids.projectIterationId() == null
                ? null
                : projectContextMapper.findIterationById(ids.projectIterationId());
        if (context == null || space == null) {
            return null;
        }
        if (!providerId.equals(context.getProviderId()) || !providerId.equals(space.getProviderId())) {
            return null;
        }
        if (!context.getId().equals(space.getProjectContextId())) {
            return null;
        }
        if (iteration != null
                && (!providerId.equals(iteration.getProviderId())
                || !space.getId().equals(iteration.getProjectSpaceId()))) {
            return null;
        }
        if (!syncedScopeStillExists(providerId, syncedScopeKeys, context, space, iteration)) {
            return null;
        }
        return new SyncedScope(context, space, iteration);
    }

    private SyncedScope resolveSavedExternalScope(String providerId, Set<String> syncedScopeKeys) {
        ProjectDataScopeSelectionDto selection = settingsService.activeScopeSelection(providerId);
        if (isBlank(selection.externalProjectId()) || isBlank(selection.externalSpaceId())) {
            return null;
        }
        ProjectContextEntity context = projectContextMapper.findContextByProviderAndExternalProjectId(
                providerId,
                selection.externalProjectId()
        );
        if (context == null) {
            return null;
        }
        ProjectSpaceEntity space = projectContextMapper.findSpaceByProviderAndExternalSpaceId(
                providerId,
                context.getId(),
                selection.externalSpaceId()
        );
        if (space == null) {
            return null;
        }
        ProjectIterationEntity iteration = isBlank(selection.externalIterationId())
                ? null
                : projectContextMapper.findIterationByProviderAndExternalIterationId(
                        providerId,
                        space.getId(),
                        selection.externalIterationId()
                );
        if (!isBlank(selection.externalIterationId()) && iteration == null) {
            return null;
        }
        if (!syncedScopeStillExists(providerId, syncedScopeKeys, context, space, iteration)) {
            return null;
        }
        return new SyncedScope(context, space, iteration);
    }

    private boolean syncedScopeStillExists(String providerId,
                                           Set<String> syncedScopeKeys,
                                           ProjectContextEntity context,
                                           ProjectSpaceEntity space,
                                           ProjectIterationEntity iteration) {
        if (iteration != null) {
            return syncedScopeKeys.contains(scopeKey(providerId, context, space, iteration));
        }
        String prefix = providerId
                + "|"
                + context.getExternalProjectId()
                + "|"
                + space.getExternalSpaceId()
                + "|";
        return syncedScopeKeys.stream().anyMatch(key -> key.startsWith(prefix));
    }

    private String scopeKey(String providerId, SyncedScope scope) {
        return scopeKey(providerId, scope.context(), scope.space(), scope.iteration());
    }

    private String scopeKey(String providerId,
                            ProjectContextEntity context,
                            ProjectSpaceEntity space,
                            ProjectIterationEntity iteration) {
        return providerId
                + "|"
                + context.getExternalProjectId()
                + "|"
                + space.getExternalSpaceId()
                + "|"
                + (iteration != null ? iteration.getExternalIterationId() : "");
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
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

    private record SyncResult(
            SyncedScope activeScope,
            ProjectDataSyncStatsDto stats
    ) {}

    private enum UpsertWorkItemResult {
        CREATED,
        UPDATED
    }
}
