package com.agentcenter.bridge.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

@Service
public class ConfirmationService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfirmationMapper confirmationMapper;
    private final WorkflowCommandService workflowCommandService;
    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;

    public ConfirmationService(ConfirmationMapper confirmationMapper,
                               WorkflowCommandService workflowCommandService,
                               WorkItemMapper workItemMapper,
                               WorkflowMapper workflowMapper) {
        this.confirmationMapper = confirmationMapper;
        this.workflowCommandService = workflowCommandService;
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
    }

    public List<ConfirmationRequestDto> listPending() {
        return confirmationMapper.findByStatus("PENDING").stream()
                .map(this::toDto).toList();
    }

    public List<ConfirmationRequestDto> listByStatus(String status) {
        return confirmationMapper.findByStatus(status).stream()
                .map(this::toDto).toList();
    }

    public ConfirmationRequestDto getById(String id) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        return toDto(entity);
    }

    @Transactional
    public ConfirmationRequestDto enterSession(String id) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        if (!ConfirmationStatus.PENDING.name().equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirmation is not PENDING");
        }
        entity.setStatus(ConfirmationStatus.IN_CONVERSATION.name());
        entity.setUpdatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        confirmationMapper.update(entity);
        return toDto(entity);
    }

    @Transactional
    public ConfirmationRequestDto resolve(String id, ResolveConfirmationRequest request) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        String currentStatus = entity.getStatus();
        if (!ConfirmationStatus.PENDING.name().equals(currentStatus)
                && !ConfirmationStatus.IN_CONVERSATION.name().equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot resolve confirmation in status: " + currentStatus);
        }

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.RESOLVED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(request.comment());
        entity.setUpdatedAt(now);
        if (request.payload() != null) {
            try {
                entity.setResolutionPayloadJson(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(request.payload()));
            } catch (Exception ignored) {
            }
        }
        confirmationMapper.update(entity);

        // resolve() always advances — reject goes through the /reject endpoint
        var nodeInstanceId = entity.getWorkflowNodeInstanceId();
        if (nodeInstanceId != null) {
            workflowCommandService.completeNodeAndScheduleAdvance(nodeInstanceId);
        }

        return toDto(entity);
    }

    @Transactional
    public ConfirmationRequestDto reject(String id, String comment) {
        var entity = confirmationMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Confirmation not found: " + id);
        }
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        entity.setStatus(ConfirmationStatus.REJECTED.name());
        entity.setResolvedBy("admin");
        entity.setResolvedAt(now);
        entity.setResolutionComment(comment);
        entity.setUpdatedAt(now);
        confirmationMapper.update(entity);
        return toDto(entity);
    }

    private ConfirmationRequestDto toDto(ConfirmationRequestEntity e) {
        WorkItemEntity workItem = e.getWorkItemId() != null
                ? workItemMapper.findById(e.getWorkItemId())
                : null;
        String workflowNodeName = resolveWorkflowNodeName(e.getWorkflowNodeInstanceId());
        return new ConfirmationRequestDto(
                e.getId(),
                ConfirmationRequestType.valueOf(e.getRequestType()),
                ConfirmationStatus.valueOf(e.getStatus()),
                e.getWorkItemId(),
                workItem != null ? workItem.getCode() : null,
                workItem != null ? WorkItemType.valueOf(workItem.getType()) : null,
                workItem != null ? workItem.getTitle() : null,
                e.getWorkflowInstanceId(),
                e.getWorkflowNodeInstanceId(),
                workflowNodeName,
                e.getAgentSessionId(),
                e.getSkillName(),
                e.getTitle(),
                e.getContent(),
                e.getContextSummary(),
                e.getOptionsJson(),
                e.getPriority() != null ? Priority.valueOf(e.getPriority()) : Priority.MEDIUM,
                parseDateTime(e.getCreatedAt())
        );
    }

    private String resolveWorkflowNodeName(String nodeInstanceId) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return null;
        }
        var node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            return null;
        }
        var instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        if (instance == null) {
            return null;
        }
        return workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId()).stream()
                .filter(def -> def.getId().equals(node.getNodeDefinitionId()))
                .findFirst()
                .map(WorkflowNodeDefinitionEntity::getName)
                .orElse(null);
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
}
