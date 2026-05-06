package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.CreateWorkItemRequest;
import com.agentcenter.bridge.api.dto.UpdateWorkItemRequest;
import com.agentcenter.bridge.api.dto.WorkItemDto;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WorkItemService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkItemMapper workItemMapper;
    private final IdGenerator idGenerator;

    public WorkItemService(WorkItemMapper workItemMapper, IdGenerator idGenerator) {
        this.workItemMapper = workItemMapper;
        this.idGenerator = idGenerator;
    }

    public List<WorkItemDto> listWorkItems() {
        return workItemMapper.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public WorkItemDto getWorkItem(String id) {
        var entity = workItemMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found");
        }
        return toDto(entity);
    }

    public WorkItemDto createWorkItem(CreateWorkItemRequest request) {
        var entity = new WorkItemEntity();
        entity.setId(idGenerator.nextId());
        entity.setCode(generateCode(request.type()));
        entity.setType(request.type().name());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setStatus("BACKLOG");
        entity.setPriority(request.priority() != null ? request.priority().name() : "MEDIUM");
        entity.setProjectId(request.projectId());
        entity.setSpaceId(request.spaceId());
        entity.setIterationId(request.iterationId());
        entity.setAssigneeUserId(request.assigneeUserId());
        entity.setVersion(1);
        var now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        workItemMapper.insert(entity);
        return toDto(entity);
    }

    public WorkItemDto updateWorkItem(String id, UpdateWorkItemRequest request) {
        var entity = workItemMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found");
        }
        if (request.title() != null) entity.setTitle(request.title());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.status() != null) entity.setStatus(request.status().name());
        if (request.priority() != null) entity.setPriority(request.priority().name());
        if (request.assigneeUserId() != null) entity.setAssigneeUserId(request.assigneeUserId());
        workItemMapper.update(entity);
        return toDto(entity);
    }

    private WorkItemDto toDto(WorkItemEntity e) {
        return new WorkItemDto(
                e.getId(),
                e.getCode(),
                WorkItemType.valueOf(e.getType()),
                e.getTitle(),
                e.getDescription(),
                WorkItemStatus.valueOf(e.getStatus()),
                Priority.valueOf(e.getPriority()),
                e.getProjectId(),
                e.getSpaceId(),
                e.getIterationId(),
                e.getAssigneeUserId(),
                e.getCurrentWorkflowInstanceId(),
                parseDateTime(e.getCreatedAt()),
                parseDateTime(e.getUpdatedAt())
        );
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SQLITE_DATETIME).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return OffsetDateTime.parse(value);
        }
    }

    private String generateCode(WorkItemType type) {
        var count = workItemMapper.findAll().stream()
                .filter(e -> e.getType().equals(type.name()))
                .count();
        return type.name() + String.format("%04d", count + 1);
    }
}
