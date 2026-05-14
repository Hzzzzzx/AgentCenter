package com.agentcenter.bridge.application;

import com.agentcenter.bridge.api.dto.CreateWorkItemRequest;
import com.agentcenter.bridge.api.dto.UpdateWorkItemRequest;
import com.agentcenter.bridge.api.dto.WorkflowSummaryDto;
import com.agentcenter.bridge.api.dto.WorkItemDto;
import com.agentcenter.bridge.api.dto.WorkItemOverviewDto;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkItemService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<WorkItemType, List<String>> DEFAULT_STAGE_LABELS = Map.of(
            WorkItemType.FE, List.of("需求", "方案", "实施", "验证", "归档"),
            WorkItemType.US, List.of("故事", "验收", "拆分", "评审", "归档"),
            WorkItemType.TASK, List.of("理解", "计划", "执行", "验证", "总结"),
            WorkItemType.WORK, List.of("分析", "Runbook", "执行", "校验", "报告"),
            WorkItemType.BUG, List.of("复现", "根因", "修复", "回归", "关闭"),
            WorkItemType.VULN, List.of("分级", "影响", "修复", "验证", "归档")
    );

    private final WorkItemMapper workItemMapper;
    private final WorkflowMapper workflowMapper;
    private final ConfirmationMapper confirmationMapper;
    private final IdGenerator idGenerator;

    public WorkItemService(WorkItemMapper workItemMapper,
                           WorkflowMapper workflowMapper,
                           ConfirmationMapper confirmationMapper,
                           IdGenerator idGenerator) {
        this.workItemMapper = workItemMapper;
        this.workflowMapper = workflowMapper;
        this.confirmationMapper = confirmationMapper;
        this.idGenerator = idGenerator;
    }

    public List<WorkItemDto> listWorkItems() {
        return listWorkItems(null, null, null, null);
    }

    public List<WorkItemDto> listWorkItems(String projectId, String spaceId, String iterationId) {
        return listWorkItems(null, projectId, spaceId, iterationId);
    }

    public List<WorkItemDto> listWorkItems(String providerId, String projectId, String spaceId, String iterationId) {
        if (isBlank(providerId) && isBlank(projectId) && isBlank(spaceId) && isBlank(iterationId)) {
            return workItemMapper.findAll().stream()
                    .map(this::toDto)
                    .toList();
        }
        return workItemMapper.findByScope(clean(providerId), clean(projectId), clean(spaceId), clean(iterationId)).stream()
                .map(this::toDto)
                .toList();
    }

    public WorkItemOverviewDto getOverview() {
        return getOverview(null, null, null);
    }

    public WorkItemOverviewDto getOverview(String projectId, String spaceId, String iterationId) {
        return getOverview(null, projectId, spaceId, iterationId);
    }

    public WorkItemOverviewDto getOverview(String providerId, String projectId, String spaceId, String iterationId) {
        var workItems = listWorkItems(providerId, projectId, spaceId, iterationId);
        var stats = Arrays.stream(WorkItemType.values())
                .map(type -> buildOverviewTypeStat(type, workItems))
                .toList();
        return new WorkItemOverviewDto("DATABASE", OffsetDateTime.now(ZoneOffset.UTC), stats);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        entity.setProjectId(ProjectDefaults.resolveProjectId(request.projectId()));
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
                e.getProviderId(),
                e.getExternalWorkItemId(),
                e.getProjectId(),
                e.getSpaceId(),
                e.getIterationId(),
                e.getProjectContextId(),
                e.getProjectSpaceId(),
                e.getProjectIterationId(),
                e.getAssigneeUserId(),
                e.getCurrentWorkflowInstanceId(),
                buildWorkflowSummary(e.getCurrentWorkflowInstanceId()),
                parseDateTime(e.getCreatedAt()),
                parseDateTime(e.getUpdatedAt())
        );
    }

    private WorkItemOverviewDto.TypeStat buildOverviewTypeStat(WorkItemType type, List<WorkItemDto> allWorkItems) {
        var items = allWorkItems.stream()
                .filter(item -> item.type() == type)
                .toList();
        int runningCount = 0;
        int waitingCount = 0;
        int blockedCount = 0;
        int unstartedCount = 0;
        int completedCount = 0;
        int completedNodeCount = 0;
        int totalNodeCount = 0;
        Map<String, WorkItemOverviewDto.NodeDistribution> nodeDistribution = new java.util.HashMap<>();

        for (var item : items) {
            var stages = overviewStagesFor(item);
            boolean hasRunning = stages.stream().anyMatch(stage -> "RUNNING".equals(stage.status()));
            boolean hasWaiting = stages.stream().anyMatch(stage ->
                    "WAITING_CONFIRMATION".equals(stage.status()) || stage.pendingConfirmationCount() > 0);
            boolean hasFailed = stages.stream().anyMatch(stage -> "FAILED".equals(stage.status()))
                    || hasWorkflowStatus(item, "FAILED")
                    || hasWorkflowStatus(item, "PAUSED")
                    || hasWorkflowStatus(item, "BLOCKED");
            boolean isCompleted = hasWorkflowStatus(item, "COMPLETED") || item.status() == WorkItemStatus.DONE;
            boolean isUnstarted = item.workflowSummary() == null && item.currentWorkflowInstanceId() == null;

            if (hasRunning) runningCount += 1;
            if (hasWaiting) waitingCount += 1;
            if (hasFailed) blockedCount += 1;
            if (isUnstarted) unstartedCount += 1;
            if (isCompleted) completedCount += 1;

            for (var stage : stages) {
                if (isNodeComplete(stage.status())) {
                    completedNodeCount += 1;
                }
                totalNodeCount += 1;
            }

            addOverviewDistribution(nodeDistribution, currentNodeBucketFor(item, stages));
        }

        int completionRate = totalNodeCount > 0
                ? Math.round((completedNodeCount * 100f) / totalNodeCount)
                : 0;
        var distribution = nodeDistribution.values().stream()
                .sorted(Comparator
                        .comparingInt(WorkItemOverviewDto.NodeDistribution::priority)
                        .thenComparing(Comparator.comparingInt(WorkItemOverviewDto.NodeDistribution::count).reversed())
                        .thenComparing(WorkItemOverviewDto.NodeDistribution::label))
                .toList();

        return new WorkItemOverviewDto.TypeStat(
                type.name(),
                items.size(),
                runningCount,
                waitingCount,
                blockedCount,
                unstartedCount,
                completedCount,
                completedNodeCount,
                totalNodeCount,
                completionRate,
                distribution
        );
    }

    private record OverviewStage(String label, String status, int pendingConfirmationCount) {}

    private record OverviewNodeBucket(String label, int priority) {}

    private List<OverviewStage> overviewStagesFor(WorkItemDto item) {
        var summary = item.workflowSummary();
        if (summary != null && summary.stages() != null && !summary.stages().isEmpty()) {
            return summary.stages().stream()
                    .map(stage -> new OverviewStage(
                            firstNonBlank(stage.name(), stage.skillName(), "阶段"),
                            stage.status(),
                            stage.pendingConfirmationCount()))
                    .toList();
        }
        if (summary != null && summary.nodes() != null && !summary.nodes().isEmpty()) {
            return summary.nodes().stream()
                    .map(node -> new OverviewStage(
                            firstNonBlank(node.definitionName(), node.skillName(), "阶段"),
                            node.status(),
                            0))
                    .toList();
        }
        return defaultStageNamesFor(item.projectId(), item.type()).stream()
                .map(label -> new OverviewStage(label, "PENDING", 0))
                .toList();
    }

    private List<String> defaultStageNamesFor(String projectId, WorkItemType type) {
        String resolvedProjectId = ProjectDefaults.resolveProjectId(projectId);
        var definitions = workflowMapper.findDefinitionsByProjectIdAndWorkItemType(resolvedProjectId, type.name());
        if (definitions.isEmpty() && !ProjectDefaults.DEFAULT_PROJECT_ID.equals(resolvedProjectId)) {
            definitions = workflowMapper.findDefinitionsByProjectIdAndWorkItemType(
                    ProjectDefaults.DEFAULT_PROJECT_ID, type.name());
        }
        var availableDefinitions = definitions;
        var definition = availableDefinitions.stream()
                .filter(candidate -> "ENABLED".equals(candidate.getStatus()))
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsDefault()))
                .findFirst()
                .orElseGet(() -> availableDefinitions.stream()
                        .filter(candidate -> "ENABLED".equals(candidate.getStatus()))
                        .findFirst()
                        .orElse(null));
        if (definition != null) {
            var names = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(definition.getId()).stream()
                    .map(node -> firstNonBlank(node.getName(), node.getSkillName(), "阶段"))
                    .toList();
            if (!names.isEmpty()) {
                return names;
            }
        }
        return DEFAULT_STAGE_LABELS.getOrDefault(type, List.of("待处理"));
    }

    private OverviewNodeBucket currentNodeBucketFor(WorkItemDto item, List<OverviewStage> stages) {
        var waitingStage = stages.stream()
                .filter(stage -> "WAITING_CONFIRMATION".equals(stage.status()) || stage.pendingConfirmationCount() > 0)
                .findFirst();
        if (waitingStage.isPresent()) {
            return new OverviewNodeBucket(waitingStage.get().label(), 0);
        }

        var runningStage = stages.stream()
                .filter(stage -> "RUNNING".equals(stage.status()))
                .findFirst();
        if (runningStage.isPresent()) {
            return new OverviewNodeBucket(runningStage.get().label(), 1);
        }

        var failedStage = stages.stream()
                .filter(stage -> "FAILED".equals(stage.status()))
                .findFirst();
        if (failedStage.isPresent()) {
            return new OverviewNodeBucket(failedStage.get().label(), 2);
        }

        if (hasWorkflowStatus(item, "COMPLETED") || item.status() == WorkItemStatus.DONE) {
            return new OverviewNodeBucket("已完成", 5);
        }

        if (item.workflowSummary() == null && item.currentWorkflowInstanceId() == null) {
            return new OverviewNodeBucket("未开始", 4);
        }

        var nextStage = stages.stream()
                .filter(stage -> !isNodeComplete(stage.status()))
                .findFirst();
        if (nextStage.isPresent()) {
            return new OverviewNodeBucket(nextStage.get().label(), 3);
        }

        if (hasWorkflowStatus(item, "PAUSED")) {
            return new OverviewNodeBucket("已暂停", 2);
        }

        if (hasWorkflowStatus(item, "FAILED") || hasWorkflowStatus(item, "BLOCKED")) {
            return new OverviewNodeBucket("异常", 2);
        }

        return new OverviewNodeBucket("待处理", 3);
    }

    private void addOverviewDistribution(
            Map<String, WorkItemOverviewDto.NodeDistribution> distribution,
            OverviewNodeBucket bucket) {
        var current = distribution.get(bucket.label());
        int count = current != null ? current.count() + 1 : 1;
        int priority = current != null ? Math.min(current.priority(), bucket.priority()) : bucket.priority();
        distribution.put(bucket.label(), new WorkItemOverviewDto.NodeDistribution(bucket.label(), count, priority));
    }

    private boolean hasWorkflowStatus(WorkItemDto item, String status) {
        return item.workflowSummary() != null && status.equals(item.workflowSummary().status());
    }

    private boolean isNodeComplete(String status) {
        return "COMPLETED".equals(status) || "SKIPPED".equals(status);
    }

    private String firstNonBlank(String primary, String fallback, String defaultValue) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return defaultValue;
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
        Map<String, Long> pendingConfirmationsByNode = confirmationMapper.findByWorkItemId(instance.getWorkItemId()).stream()
                .filter(confirmation -> confirmation.getWorkflowNodeInstanceId() != null)
                .filter(confirmation -> "PENDING".equals(confirmation.getStatus())
                        || "IN_CONVERSATION".equals(confirmation.getStatus()))
                .collect(Collectors.groupingBy(
                        confirmation -> confirmation.getWorkflowNodeInstanceId(),
                        Collectors.counting()));

        var stages = new ArrayList<WorkflowSummaryDto.StageSummary>();
        for (var stageNode : stageNodes) {
            var def = definitionById.get(stageNode.getNodeDefinitionId());
            String stageKey = stageKey(stageNode, def);
            List<WorkflowNodeInstanceEntity> children = dynamicNodes.stream()
                    .filter(child -> belongsToStage(child, stageNode, stageKey))
                    .toList();
            String stageStatus = aggregateStageStatus(stageNode, children);
            int pendingConfirmations = confirmationCount(stageNode, pendingConfirmationsByNode) + children.stream()
                    .mapToInt(child -> confirmationCount(child, pendingConfirmationsByNode))
                    .sum();
            String name = def != null ? def.getName() : null;
            String skillName = stageNode.getSkillName() != null ? stageNode.getSkillName()
                    : (def != null ? def.getSkillName() : null);
            String latestSummary = latestSummary(stageNode, children, name);
            String errorMessage = latestErrorMessage(stageNode, children);
            stages.add(new WorkflowSummaryDto.StageSummary(
                    stageNode.getId(),
                    stageKey,
                    name,
                    skillName,
                    stageStatus,
                    children.size(),
                    (int) children.stream().filter(this::isRecoveryNode).count(),
                    pendingConfirmations,
                    latestSummary,
                    errorMessage
            ));
        }

        var nodes = stages.stream()
                .map(stage -> new WorkflowSummaryDto.NodeSummary(
                        stage.id(),
                        stage.name(),
                        stage.skillName(),
                        stage.status(),
                        stage.errorMessage()
                ))
                .toList();

        String currentStageKey = resolveCurrentStageKey(
                instance.getCurrentNodeInstanceId(),
                allNodeInstances,
                stageNodes,
                definitionById);

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

    private String resolveCurrentStageKey(String currentNodeInstanceId,
                                          List<WorkflowNodeInstanceEntity> allNodeInstances,
                                          List<WorkflowNodeInstanceEntity> stageNodes,
                                          Map<String, WorkflowNodeDefinitionEntity> definitionById) {
        if (currentNodeInstanceId == null) {
            return null;
        }
        WorkflowNodeInstanceEntity currentNode = allNodeInstances.stream()
                .filter(node -> currentNodeInstanceId.equals(node.getId()))
                .findFirst()
                .orElse(null);
        if (currentNode == null) {
            return null;
        }
        if (isStageNode(currentNode)) {
            return stageKey(currentNode, definitionById.get(currentNode.getNodeDefinitionId()));
        }
        return stageNodes.stream()
                .filter(stage -> belongsToStage(
                        currentNode,
                        stage,
                        stageKey(stage, definitionById.get(stage.getNodeDefinitionId()))))
                .findFirst()
                .map(stage -> stageKey(stage, definitionById.get(stage.getNodeDefinitionId())))
                .orElse(currentNode.getStageKey());
    }

    private String aggregateStageStatus(WorkflowNodeInstanceEntity stage,
                                        List<WorkflowNodeInstanceEntity> children) {
        String stageStatus = stage.getStatus();
        if ("WAITING_CONFIRMATION".equals(stageStatus)
                || children.stream().anyMatch(node -> "WAITING_CONFIRMATION".equals(node.getStatus()))) {
            return "WAITING_CONFIRMATION";
        }
        if ("FAILED".equals(stageStatus)
                || children.stream().anyMatch(node -> "FAILED".equals(node.getStatus()))) {
            return "FAILED";
        }
        if ("RUNNING".equals(stageStatus)
                || children.stream().anyMatch(node -> "RUNNING".equals(node.getStatus()))) {
            return "RUNNING";
        }
        if (!children.isEmpty()
                && !"SKIPPED".equals(stageStatus)
                && children.stream().allMatch(node -> isNodeComplete(node.getStatus()))) {
            return "COMPLETED";
        }
        return stageStatus;
    }

    private int confirmationCount(WorkflowNodeInstanceEntity node, Map<String, Long> pendingConfirmationsByNode) {
        return pendingConfirmationsByNode.getOrDefault(node.getId(), 0L).intValue();
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

    private String latestErrorMessage(WorkflowNodeInstanceEntity stage,
                                      List<WorkflowNodeInstanceEntity> children) {
        if (stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank()) {
            return stage.getErrorMessage();
        }
        return children.stream()
                .filter(child -> child.getErrorMessage() != null && !child.getErrorMessage().isBlank())
                .reduce((first, second) -> second)
                .map(WorkflowNodeInstanceEntity::getErrorMessage)
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

    private String generateCode(WorkItemType type) {
        var count = workItemMapper.findAll().stream()
                .filter(e -> e.getType().equals(type.name()))
                .count();
        return type.name() + String.format("%04d", count + 1);
    }
}
