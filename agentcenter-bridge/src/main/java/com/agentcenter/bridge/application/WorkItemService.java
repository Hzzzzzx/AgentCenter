package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.CreateWorkItemRequest;
import com.agentcenter.bridge.api.dto.UpdateWorkItemRequest;
import com.agentcenter.bridge.api.dto.WorkflowSummaryDto;
import com.agentcenter.bridge.api.dto.WorkItemDto;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkItemService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;
    private final IdGenerator idGenerator;

    public WorkItemService(WorkItemMapper workItemMapper, WorkflowMapper workflowMapper, IdGenerator idGenerator) {
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
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
        entity.setUpdatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
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
                buildWorkflowSummary(e.getCurrentWorkflowInstanceId()),
                parseDateTime(e.getCreatedAt()),
                parseDateTime(e.getUpdatedAt())
        );
    }

    private WorkflowSummaryDto buildWorkflowSummary(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            return null;
        }
        var allNodeInstances = workflowMapper.findNodeInstancesByWorkflowInstanceId(instanceId);
        var nodeDefinitions = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());
        Map<String, WorkflowNodeDefinitionEntity> definitionById = nodeDefinitions.stream()
                .collect(Collectors.toMap(WorkflowNodeDefinitionEntity::getId, d -> d));

        Map<String, Integer> orderByDefinitionId = nodeDefinitions.stream()
                .collect(Collectors.toMap(
                        WorkflowNodeDefinitionEntity::getId,
                        WorkflowNodeDefinitionEntity::getOrderNo));

        var stageNodes = allNodeInstances.stream()
                .filter(this::isStageNode)
                .sorted(Comparator.comparingInt(ni ->
                        sequenceFor(ni, orderByDefinitionId)))
                .toList();

        var dynamicNodes = allNodeInstances.stream()
                .filter(ni -> !isStageNode(ni))
                .toList();

        var stages = new ArrayList<WorkflowSummaryDto.StageSummary>();
        for (var stageNode : stageNodes) {
            var def = definitionById.get(stageNode.getNodeDefinitionId());
            String stageKey = stageKey(stageNode, def);
            List<WorkflowNodeInstanceEntity> children = dynamicNodes.stream()
                    .filter(child -> belongsToStage(child, stageNode, stageKey))
                    .toList();
            String stageStatus = aggregateStageStatus(stageNode, children);
            int pendingConfirmations = confirmationCount(stageNode) + children.stream()
                    .mapToInt(this::confirmationCount)
                    .sum();
            String name = def != null ? def.getName() : null;
            String skillName = stageNode.getSkillName() != null ? stageNode.getSkillName()
                    : (def != null ? def.getSkillName() : null);
            String latestSummary = latestSummary(stageNode, children, name);
            stages.add(new WorkflowSummaryDto.StageSummary(
                    stageNode.getId(),
                    stageKey,
                    name,
                    skillName,
                    stageStatus,
                    children.size(),
                    (int) children.stream().filter(this::isRecoveryNode).count(),
                    pendingConfirmations,
                    latestSummary
            ));
        }

        var nodes = stages.stream()
                .map(stage -> new WorkflowSummaryDto.NodeSummary(
                        stage.id(),
                        stage.name(),
                        stage.skillName(),
                        stage.status()
                ))
                .toList();

        String currentStageKey = stageNodes.stream()
                .filter(ni -> ni.getId().equals(instance.getCurrentNodeInstanceId()))
                .findFirst()
                .map(ni -> stageKey(ni, definitionById.get(ni.getNodeDefinitionId())))
                .orElse(null);

        return new WorkflowSummaryDto(
                instance.getId(),
                instance.getStatus(),
                instance.getCurrentNodeInstanceId(),
                currentStageKey,
                nodes,
                stages
        );
    }

    private boolean isStageNode(WorkflowNodeInstanceEntity node) {
        return node.getNodeKind() == null || node.getNodeKind().isBlank()
                || "STAGE".equals(node.getNodeKind());
    }

    private boolean isRecoveryNode(WorkflowNodeInstanceEntity node) {
        return "RECOVERY".equals(node.getNodeKind());
    }

    private int sequenceFor(WorkflowNodeInstanceEntity node, Map<String, Integer> orderByDefinitionId) {
        if (node.getSequenceNo() != null) {
            return node.getSequenceNo();
        }
        return orderByDefinitionId.getOrDefault(node.getNodeDefinitionId(), Integer.MAX_VALUE);
    }

    private String stageKey(WorkflowNodeInstanceEntity node, WorkflowNodeDefinitionEntity definition) {
        if (node.getStageKey() != null && !node.getStageKey().isBlank()) {
            return node.getStageKey();
        }
        if (definition != null && definition.getStageKey() != null && !definition.getStageKey().isBlank()) {
            return definition.getStageKey();
        }
        return definition != null ? definition.getNodeKey() : null;
    }

    private boolean belongsToStage(WorkflowNodeInstanceEntity child,
                                   WorkflowNodeInstanceEntity stage,
                                   String stageKey) {
        if (child.getParentNodeInstanceId() != null && child.getParentNodeInstanceId().equals(stage.getId())) {
            return true;
        }
        return stageKey != null && stageKey.equals(child.getStageKey());
    }

    private String aggregateStageStatus(WorkflowNodeInstanceEntity stage,
                                        List<WorkflowNodeInstanceEntity> children) {
        if (stage.getStatus() != null && !"PENDING".equals(stage.getStatus())) {
            return stage.getStatus();
        }
        if (children.stream().anyMatch(node -> "WAITING_CONFIRMATION".equals(node.getStatus()))) {
            return "WAITING_CONFIRMATION";
        }
        if (children.stream().anyMatch(node -> "FAILED".equals(node.getStatus()))) {
            return "FAILED";
        }
        if (children.stream().anyMatch(node -> "RUNNING".equals(node.getStatus()))) {
            return "RUNNING";
        }
        if (!children.isEmpty() && children.stream().allMatch(node ->
                "COMPLETED".equals(node.getStatus()) || "SKIPPED".equals(node.getStatus()))) {
            return "COMPLETED";
        }
        return stage.getStatus();
    }

    private int confirmationCount(WorkflowNodeInstanceEntity node) {
        return "WAITING_CONFIRMATION".equals(node.getStatus()) ? 1 : 0;
    }

    private String latestSummary(WorkflowNodeInstanceEntity stage,
                                 List<WorkflowNodeInstanceEntity> children,
                                 String fallback) {
        return children.stream()
                .filter(child -> child.getSummary() != null && !child.getSummary().isBlank())
                .reduce((first, second) -> second)
                .map(WorkflowNodeInstanceEntity::getSummary)
                .orElse(stage.getSummary() != null && !stage.getSummary().isBlank()
                        ? stage.getSummary()
                        : fallback);
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
