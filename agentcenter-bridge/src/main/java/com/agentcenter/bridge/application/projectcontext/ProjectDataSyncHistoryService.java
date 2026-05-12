package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectDataSyncHistoryDto;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectDataSyncHistoryEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ProjectContextMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectDataSyncHistoryService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectContextMapper projectContextMapper;
    private final IdGenerator idGenerator;

    public ProjectDataSyncHistoryService(ProjectContextMapper projectContextMapper, IdGenerator idGenerator) {
        this.projectContextMapper = projectContextMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProjectDataSyncHistoryEntity start(String providerId) {
        ProjectDataSyncHistoryEntity entity = new ProjectDataSyncHistoryEntity();
        entity.setId(idGenerator.nextId());
        entity.setProviderId(providerId);
        entity.setStatus("RUNNING");
        entity.setContextCount(0);
        entity.setWorkItemCount(0);
        entity.setStartedAt(now());
        projectContextMapper.insertSyncHistory(entity);
        return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(ProjectDataSyncHistoryEntity entity,
                            int contextCount,
                            int workItemCount,
                            String activeProjectContextId,
                            String activeProjectSpaceId,
                            String activeProjectIterationId,
                            String resultJson) {
        entity.setStatus("SUCCESS");
        entity.setContextCount(contextCount);
        entity.setWorkItemCount(workItemCount);
        entity.setActiveProjectContextId(activeProjectContextId);
        entity.setActiveProjectSpaceId(activeProjectSpaceId);
        entity.setActiveProjectIterationId(activeProjectIterationId);
        entity.setResultJson(resultJson);
        entity.setErrorMessage(null);
        entity.setCompletedAt(now());
        projectContextMapper.updateSyncHistory(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(ProjectDataSyncHistoryEntity entity, String errorMessage) {
        entity.setStatus("FAILED");
        entity.setErrorMessage(errorMessage == null || errorMessage.isBlank()
                ? "Unknown project data sync failure"
                : errorMessage);
        entity.setCompletedAt(now());
        projectContextMapper.updateSyncHistory(entity);
    }

    public List<ProjectDataSyncHistoryDto> list(String providerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return projectContextMapper.findSyncHistory(clean(providerId), safeLimit).stream()
                .map(this::toDto)
                .toList();
    }

    private ProjectDataSyncHistoryDto toDto(ProjectDataSyncHistoryEntity entity) {
        return new ProjectDataSyncHistoryDto(
                entity.getId(),
                entity.getProviderId(),
                entity.getStatus(),
                entity.getContextCount(),
                entity.getWorkItemCount(),
                entity.getActiveProjectContextId(),
                entity.getActiveProjectSpaceId(),
                entity.getActiveProjectIterationId(),
                entity.getResultJson(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATETIME);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
