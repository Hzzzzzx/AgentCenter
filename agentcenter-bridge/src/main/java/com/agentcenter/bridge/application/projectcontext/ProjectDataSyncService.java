package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectProviderWorkItemDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ProjectDataSyncService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectDataProviderSettingsService settingsService;
    private final WorkItemMapper workItemMapper;
    private final IdGenerator idGenerator;

    public ProjectDataSyncService(ProjectDataProviderSettingsService settingsService,
                                  WorkItemMapper workItemMapper,
                                  IdGenerator idGenerator) {
        this.settingsService = settingsService;
        this.workItemMapper = workItemMapper;
        this.idGenerator = idGenerator;
    }

    public ProjectDataSnapshotDto snapshot() {
        return settingsService.activeProvider().snapshot();
    }

    @Transactional
    public ProjectDataSnapshotDto sync() {
        ProjectDataSnapshotDto snapshot = snapshot();
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        for (ProjectProviderWorkItemDto item : snapshot.workItems()) {
            upsertWorkItem(item, now);
        }
        return snapshot;
    }

    private void upsertWorkItem(ProjectProviderWorkItemDto item, String now) {
        WorkItemEntity existing = workItemMapper.findByCode(item.code());
        if (existing == null) {
            WorkItemEntity entity = new WorkItemEntity();
            entity.setId(idGenerator.nextId());
            entity.setCode(item.code());
            entity.setType(item.type().name());
            entity.setTitle(item.title());
            entity.setDescription(item.description());
            entity.setStatus(item.status().name());
            entity.setPriority(item.priority().name());
            entity.setProjectId(item.project());
            entity.setSpaceId(item.space());
            entity.setIterationId(item.iteration());
            entity.setAssigneeUserId(item.assigneeUserId());
            entity.setVersion(1);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            workItemMapper.insert(entity);
            return;
        }

        existing.setType(item.type().name());
        existing.setTitle(item.title());
        existing.setDescription(item.description());
        existing.setStatus(item.status().name());
        existing.setPriority(item.priority().name());
        existing.setProjectId(item.project());
        existing.setSpaceId(item.space());
        existing.setIterationId(item.iteration());
        existing.setAssigneeUserId(item.assigneeUserId());
        existing.setUpdatedAt(now);
        workItemMapper.updateFromSync(existing);
    }
}
