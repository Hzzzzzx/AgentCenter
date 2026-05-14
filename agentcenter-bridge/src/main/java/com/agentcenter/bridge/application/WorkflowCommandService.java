package com.agentcenter.bridge.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentcenter.bridge.api.dto.AgentSessionDto;
import com.agentcenter.bridge.api.dto.ArtifactDto;
import com.agentcenter.bridge.api.dto.BatchStartWorkflowsRequest;
import com.agentcenter.bridge.api.dto.BatchStartWorkflowsResponse;
import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.RestartWorkflowRequest;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.api.dto.StartWorkflowRequest;
import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.api.dto.WorkflowInstanceDto;
import com.agentcenter.bridge.api.dto.WorkflowNodeInstanceDto;
import com.agentcenter.bridge.api.dto.WorkflowVersionDto;
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.application.runtime.SkillInvocationRequest;
import com.agentcenter.bridge.application.runtime.SkillRunResult;
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.session.ContentFormat;
import com.agentcenter.bridge.domain.session.MessageRole;
import com.agentcenter.bridge.domain.session.MessageStatus;
import com.agentcenter.bridge.domain.session.SessionStatus;
import com.agentcenter.bridge.domain.session.SessionType;
import com.agentcenter.bridge.application.workflow.InteractionMapper;
import com.agentcenter.bridge.application.workflow.WorkflowNodeTransitionGuard;
import com.agentcenter.bridge.application.workflow.WorkflowPromptComposer;
import com.agentcenter.bridge.application.workflow.WorkflowResumeState;
import com.agentcenter.bridge.application.workflow.WorkflowTransitionDecision;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.domain.workflow.WorkflowNodeStatus;
import com.agentcenter.bridge.domain.workflow.WorkflowStatus;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteraction;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeState;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateParser;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

import jakarta.annotation.PreDestroy;

@Service
public class WorkflowCommandService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCommandService.class);
    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String WORKFLOW_MODE_AUTO = "AUTO";
    private static final String WORKFLOW_MODE_MANUAL_CONFIRM = "MANUAL_CONFIRM";
    private static final String WORKFLOW_MODE_START_OR_CONTINUE = "START_OR_CONTINUE";
    private static final int DEFAULT_BATCH_START_LIMIT = 5;
    private static final int MAX_BATCH_START_LIMIT = 20;
    private static final int ARTIFACT_SCAN_MAX_DEPTH = 8;
    private static final long MAX_INLINE_ARTIFACT_BYTES = 2_000_000L;
    private static final Set<String> ARTIFACT_FILE_EXTENSIONS = Set.of(
            ".md", ".markdown", ".json", ".yaml", ".yml", ".patch", ".diff", ".txt");
    private static final Set<String> ARTIFACT_SCAN_EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".opencode", ".idea", ".vscode", "node_modules", "target", "dist", "build");
    private static final String BATCH_STATUS_STARTED = "STARTED";
    private static final String BATCH_STATUS_SKIPPED = "SKIPPED";
    private static final String BATCH_STATUS_FAILED = "FAILED";
    private static final String FALLBACK_DECISION_OPTIONS_JSON = "["
            + "{\"id\":\"REQUEST_OPTIONS\",\"label\":\"请 Agent 给出 2-3 个选项\"},"
            + "{\"id\":\"CUSTOM_ANSWER\",\"label\":\"我直接补充答案\"}"
            + "]";
    private final WorkflowMapper workflowMapper;
    private final WorkItemMapper workItemMapper;
    private final ArtifactMapper artifactMapper;
    private final ConfirmationMapper confirmationMapper;
    private final AgentSessionService agentSessionService;
    private final RuntimeEventService runtimeEventService;
    private final AgentMessageWriteService agentMessageWriteService;
    private final RuntimeGateway runtimeGateway;
    private final SkillRegistryService skillRegistryService;
    private final ConfirmationCreatedEventPayloadBuilder confirmationCreatedPayloadBuilder;
    private final WorkflowContextAnchorService workflowContextAnchorService;
    private final ProjectRuntimeWorkspaceResolver workspaceResolver;
    private final IdGenerator idGenerator;
    private final RuntimeType workflowRuntimeType;
    private final ExecutorService workflowExecutor;
    private final InteractionMapper interactionMapper = new InteractionMapper();
    private final WorkflowPromptComposer workflowPromptComposer = new WorkflowPromptComposer();
    private final WorkflowNodeTransitionGuard workflowNodeTransitionGuard = new WorkflowNodeTransitionGuard();

    public WorkflowCommandService(WorkflowMapper workflowMapper,
                                   WorkItemMapper workItemMapper,
                                   ArtifactMapper artifactMapper,
                                   ConfirmationMapper confirmationMapper,
                                   AgentSessionService agentSessionService,
                                   RuntimeEventService runtimeEventService,
                                   AgentMessageWriteService agentMessageWriteService,
                                   RuntimeGateway runtimeGateway,
                                   SkillRegistryService skillRegistryService,
                                   ConfirmationCreatedEventPayloadBuilder confirmationCreatedEventPayloadBuilder,
                                   WorkflowContextAnchorService workflowContextAnchorService,
                                   ProjectRuntimeWorkspaceResolver workspaceResolver,
                                   IdGenerator idGenerator,
                                   @Value("${agentcenter.runtime.default-type:OPENCODE}") String defaultRuntimeType,
                                   @Qualifier("workflowExecutor") ExecutorService workflowExecutor) {
        this.workflowMapper = workflowMapper;
        this.workItemMapper = workItemMapper;
        this.artifactMapper = artifactMapper;
        this.confirmationMapper = confirmationMapper;
        this.agentSessionService = agentSessionService;
        this.runtimeEventService = runtimeEventService;
        this.agentMessageWriteService = agentMessageWriteService;
        this.runtimeGateway = runtimeGateway;
        this.skillRegistryService = skillRegistryService;
        this.confirmationCreatedPayloadBuilder = confirmationCreatedEventPayloadBuilder;
        this.workflowContextAnchorService = workflowContextAnchorService;
        this.workspaceResolver = workspaceResolver;
        this.idGenerator = idGenerator;
        this.workflowRuntimeType = RuntimeType.valueOf(defaultRuntimeType.toUpperCase());
        this.workflowExecutor = workflowExecutor;
    }

    @PreDestroy
    public void shutdown() {
        workflowExecutor.shutdownNow();
    }

    public synchronized BatchStartWorkflowsResponse startWorkflows(BatchStartWorkflowsRequest request) {
        if (request == null || request.workItemType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workItemType is required");
        }

        List<String> workItemIds = request.workItemIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        int requestedLimit = request.limit() != null ? request.limit() : DEFAULT_BATCH_START_LIMIT;
        int effectiveLimit = normalizeBatchStartLimit(requestedLimit);
        StartWorkflowRequest startRequest = new StartWorkflowRequest(null, request.mode());
        List<BatchStartWorkflowsResponse.ItemResult> results = new ArrayList<>();
        int startedAttempts = 0;

        for (String workItemId : workItemIds) {
            WorkItemEntity workItem = workItemMapper.findById(workItemId);
            if (workItem == null) {
                results.add(batchResult(workItemId, null, BATCH_STATUS_SKIPPED, "工作项不存在", null));
                continue;
            }
            if (!request.workItemType().name().equals(workItem.getType())) {
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_SKIPPED, "工作项类型与当前筛选类型不一致", null));
                continue;
            }
            if (!isInitialStartCandidate(workItem)) {
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_SKIPPED, "工作项已开始或不在初始阶段", null));
                continue;
            }
            if (startedAttempts >= effectiveLimit) {
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_SKIPPED, "已达到单次批量启动上限", null));
                continue;
            }

            startedAttempts += 1;
            try {
                StartWorkflowResponse response = startWorkflow(workItemId, startRequest);
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_STARTED, null, response));
            } catch (ResponseStatusException e) {
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_FAILED, e.getReason(), null));
            } catch (RuntimeException e) {
                results.add(batchResult(workItemId, workItem.getCode(), BATCH_STATUS_FAILED, e.getMessage(), null));
            }
        }

        return new BatchStartWorkflowsResponse(
                request.workItemType(),
                workItemIds.size(),
                requestedLimit,
                effectiveLimit,
                countBatchStatus(results, BATCH_STATUS_STARTED),
                countBatchStatus(results, BATCH_STATUS_SKIPPED),
                countBatchStatus(results, BATCH_STATUS_FAILED),
                results
        );
    }

    public synchronized StartWorkflowResponse startWorkflow(String workItemId, StartWorkflowRequest request) {
        WorkItemEntity workItem = workItemMapper.findById(workItemId);
        if (workItem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found: " + workItemId);
        }

        WorkflowInstanceEntity existing = findActiveInstance(workItemId);
        if (existing != null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            applyRequestedExecutionMode(existing, request, now);
            touchWorkItem(existing.getWorkItemId(), now);
            PreparedWorkflowNode prepared = prepareNextPendingNode(existing.getId());
            if (prepared != null) {
                workflowExecutor.submit(() -> executePreparedNode(prepared));
            }
            WorkflowInstanceEntity latest = workflowMapper.findInstanceById(existing.getId());
            return buildResponse(latest != null ? latest : existing);
        }

        WorkflowDefinitionEntity definition = resolveDefinition(workItem, request != null ? request : new StartWorkflowRequest(null, null));
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No enabled workflow definition found for work item type: " + workItem.getType());
        }

        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(definition.getId());
        nodeDefs.sort(Comparator.comparingInt(WorkflowNodeDefinitionEntity::getOrderNo));
        validateDefinitionSkillsRunnable(workItem, nodeDefs);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(idGenerator.nextId());
        instance.setWorkItemId(workItemId);
        instance.setWorkflowDefinitionId(definition.getId());
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setExecutionMode(resolveExecutionMode(request));
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        workflowMapper.insertInstance(instance);

        for (WorkflowNodeDefinitionEntity nodeDef : nodeDefs) {
            WorkflowNodeInstanceEntity nodeInstance = new WorkflowNodeInstanceEntity();
            nodeInstance.setId(idGenerator.nextId());
            nodeInstance.setWorkflowInstanceId(instance.getId());
            nodeInstance.setNodeDefinitionId(nodeDef.getId());
            nodeInstance.setStatus(WorkflowNodeStatus.PENDING.name());
            nodeInstance.setVersion(1);
            nodeInstance.setNodeKind("STAGE");
            nodeInstance.setOrigin("DEFINITION");
            nodeInstance.setStageKey(stageKeyFor(nodeDef));
            nodeInstance.setSkillName(nodeDef.getSkillName());
            nodeInstance.setSummary(nodeDef.getName());
            nodeInstance.setSequenceNo(nodeDef.getOrderNo());
            workflowMapper.insertNodeInstance(nodeInstance);
        }

        List<WorkflowNodeInstanceEntity> nodeInstances =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId());
        nodeInstances.sort(Comparator.comparing(n -> {
            WorkflowNodeDefinitionEntity def = nodeDefs.stream()
                    .filter(d -> d.getId().equals(n.getNodeDefinitionId()))
                    .findFirst().orElse(null);
            return def != null ? def.getOrderNo() : Integer.MAX_VALUE;
        }));

        WorkflowNodeInstanceEntity firstNode = nodeInstances.get(0);
        prepareWorkflowSession(instance, firstNode, nodeDefs, workItem, now);
        insertWorkflowStartContextMessage(firstNode.getAgentSessionId(), workItem, definition, nodeDefs);

        workItem.setCurrentWorkflowInstanceId(instance.getId());
        workItem.setUpdatedAt(now);
        workItemMapper.update(workItem);

        instance = workflowMapper.findInstanceById(instance.getId());
        scheduleRunNode(instance.getId(), firstNode.getId());
        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public synchronized StartWorkflowResponse restartWorkflow(String workItemId, RestartWorkflowRequest request) {
        WorkItemEntity workItem = workItemMapper.findById(workItemId);
        if (workItem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found: " + workItemId);
        }

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        WorkflowInstanceEntity existing = findRestartTargetInstance(workItem);
        if (existing != null) {
            supersedeWorkflowInstance(existing, now, request != null ? request.reason() : null);
        }

        StartWorkflowRequest startRequest = new StartWorkflowRequest(
                request != null ? request.workflowDefinitionId() : null,
                request != null ? request.mode() : null);
        return startWorkflow(workItemId, startRequest);
    }

    public List<WorkflowVersionDto> listWorkflowVersions(String workItemId) {
        WorkItemEntity workItem = workItemMapper.findById(workItemId);
        if (workItem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found: " + workItemId);
        }

        List<WorkflowInstanceEntity> instances = workflowMapper.findInstancesByWorkItemId(workItemId);
        List<WorkflowInstanceEntity> chronological = new ArrayList<>(instances);
        chronological.sort(Comparator.comparing(instance -> nonBlank(instance.getCreatedAt(), "")));

        Map<String, Integer> versionByInstanceId = new java.util.HashMap<>();
        for (int index = 0; index < chronological.size(); index++) {
            versionByInstanceId.put(chronological.get(index).getId(), index + 1);
        }

        List<ArtifactEntity> artifacts = artifactMapper.findByWorkItemId(workItemId);
        return instances.stream()
                .map(instance -> new WorkflowVersionDto(
                        versionByInstanceId.getOrDefault(instance.getId(), 1),
                        instance.getId().equals(workItem.getCurrentWorkflowInstanceId()),
                        toInstanceDto(instance),
                        findWorkflowSession(instance),
                        (int) artifacts.stream()
                                .filter(artifact -> instance.getId().equals(artifact.getWorkflowInstanceId()))
                                .count()))
                .toList();
    }

    private void supersedeWorkflowInstance(WorkflowInstanceEntity instance, String now, String reason) {
        AgentSessionDto session = findWorkflowSession(instance);
        if (session != null) {
            try {
                agentSessionService.cancelRuntime(session.id());
            } catch (Exception e) {
                log.debug("Failed to cancel runtime before superseding workflow instance {}", instance.getId(), e);
            }
            try {
                agentSessionService.updateSessionStatus(session.id(), SessionStatus.ARCHIVED);
            } catch (Exception e) {
                log.debug("Failed to archive superseded workflow session {}", session.id(), e);
            }
        }

        cancelActiveConfirmationsForWorkflow(instance, now);

        instance.setStatus(WorkflowStatus.SUPERSEDED.name());
        instance.setCompletedAt(now);
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, session != null ? session.id() : null, instance.getWorkItemId(),
                instance.getId(), instance.getCurrentNodeInstanceId(),
                RuntimeEventType.STATUS, RuntimeEventSource.WORKFLOW,
                buildWorkflowSupersededPayload(instance.getId(), reason), null
        ));
    }

    private void cancelActiveConfirmationsForWorkflow(WorkflowInstanceEntity instance, String now) {
        for (ConfirmationRequestEntity confirmation : confirmationMapper.findByWorkItemId(instance.getWorkItemId())) {
            boolean belongsToWorkflow = instance.getId().equals(confirmation.getWorkflowInstanceId());
            boolean active = ConfirmationStatus.PENDING.name().equals(confirmation.getStatus())
                    || ConfirmationStatus.IN_CONVERSATION.name().equals(confirmation.getStatus());
            if (!belongsToWorkflow || !active) {
                continue;
            }

            confirmation.setStatus(ConfirmationStatus.CANCELLED.name());
            confirmation.setResolvedAt(now);
            confirmation.setResolvedBy("system");
            confirmation.setResolutionComment("Workflow version superseded by restart");
            confirmation.setUpdatedAt(now);
            confirmationMapper.update(confirmation);
        }
    }

    private String buildWorkflowSupersededPayload(String workflowInstanceId, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"workflowStatus\":\"").append(WorkflowStatus.SUPERSEDED.name()).append("\"");
        sb.append(",\"workflowInstanceId\":\"").append(escapeJson(workflowInstanceId)).append("\"");
        if (reason != null && !reason.isBlank()) {
            sb.append(",\"reason\":\"").append(escapeJson(reason.trim())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private boolean isInitialStartCandidate(WorkItemEntity workItem) {
        if (workItem.getCurrentWorkflowInstanceId() != null && !workItem.getCurrentWorkflowInstanceId().isBlank()) {
            return false;
        }
        if (WorkItemStatus.DONE.name().equals(workItem.getStatus())) {
            return false;
        }
        return findActiveInstance(workItem.getId()) == null;
    }

    private int normalizeBatchStartLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return DEFAULT_BATCH_START_LIMIT;
        }
        return Math.min(requestedLimit, MAX_BATCH_START_LIMIT);
    }

    private int countBatchStatus(List<BatchStartWorkflowsResponse.ItemResult> results, String status) {
        return (int) results.stream()
                .filter(result -> status.equals(result.status()))
                .count();
    }

    private BatchStartWorkflowsResponse.ItemResult batchResult(
            String workItemId,
            String code,
            String status,
            String reason,
            StartWorkflowResponse response) {
        return new BatchStartWorkflowsResponse.ItemResult(workItemId, code, status, reason, response);
    }

    public StartWorkflowResponse continueWorkflow(String instanceId) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found: " + instanceId);
        }
        ensureWorkflowMutable(instance);

        List<WorkflowNodeInstanceEntity> nodes = getOrderedNodeInstances(instanceId);

        String currentNodeId = instance.getCurrentNodeInstanceId();
        WorkflowNodeInstanceEntity currentNode = nodes.stream()
                .filter(n -> currentNodeId != null && currentNodeId.equals(n.getId()))
                .findFirst().orElse(null);

        if (currentNode != null
                && WorkflowNodeStatus.RUNNING.name().equals(currentNode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current node is running. Wait until it completes or pauses.");
        }
        if (currentNode != null
                && WorkflowNodeStatus.WAITING_CONFIRMATION.name().equals(currentNode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current node is waiting for confirmation. Resolve confirmation first.");
        }
        if (currentNode != null
                && WorkflowNodeStatus.FAILED.name().equals(currentNode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current node failed. Resolve the exception confirmation first.");
        }

        WorkflowNodeInstanceEntity nextPending = nodes.stream()
                .filter(n -> WorkflowNodeStatus.PENDING.name().equals(n.getStatus()))
                .findFirst().orElse(null);

        if (nextPending == null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            completeWorkflow(instance, currentNode, now);
            return buildResponse(instance);
        }

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        runNodeWithFailureGuard(instance, nextPending, nodeDefs, workItem, null);

        instance = workflowMapper.findInstanceById(instanceId);
        return buildResponse(instance);
    }

    public StartWorkflowResponse retryNode(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        ensureWorkflowMutable(instance);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        resolveActiveExceptionConfirmations(node, now);

        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.RUNNING.name());
        node.setStartedAt(now);
        node.setErrorMessage(null);
        workflowMapper.updateNodeInstance(node);

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        prepareWorkflowSession(instance, node, nodeDefs, workItem, now);
        executeSkillOnNode(instance, node, nodeDefs, workItem);

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    private void resolveActiveExceptionConfirmations(WorkflowNodeInstanceEntity node, String now) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        if (instance == null) {
            return;
        }

        for (ConfirmationRequestEntity confirmation : confirmationMapper.findByWorkItemId(instance.getWorkItemId())) {
            boolean activeException = node.getId().equals(confirmation.getWorkflowNodeInstanceId())
                    && ConfirmationRequestType.EXCEPTION.name().equals(confirmation.getRequestType())
                    && (ConfirmationStatus.PENDING.name().equals(confirmation.getStatus())
                    || ConfirmationStatus.IN_CONVERSATION.name().equals(confirmation.getStatus()));
            if (!activeException) {
                continue;
            }

            confirmation.setStatus(ConfirmationStatus.RESOLVED.name());
            confirmation.setResolvedAt(now);
            confirmation.setResolvedBy("system");
            confirmation.setResolutionComment("Retry started for workflow node");
            confirmation.setUpdatedAt(now);
            confirmationMapper.update(confirmation);
        }
    }

    public StartWorkflowResponse skipNode(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        ensureWorkflowMutable(instance);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.SKIPPED.name());
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);

        advanceToNextNode(instance);

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public StartWorkflowResponse completeNodeAndAdvance(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        ensureWorkflowMutable(instance);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.COMPLETED.name());
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);

        advanceToNextNode(instance);

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public StartWorkflowResponse completeNodeAndScheduleAdvance(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        ensureWorkflowMutable(instance);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.COMPLETED.name());
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);
        touchWorkItem(instance.getWorkItemId(), now);

        scheduleResume(instance.getId());

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public StartWorkflowResponse resumeNodeAfterInteraction(String nodeInstanceId) {
        return resumeNodeAfterInteraction(nodeInstanceId, null);
    }

    public StartWorkflowResponse resumeNodeAfterInteraction(String nodeInstanceId, String supplementalInput) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        ensureWorkflowMutable(instance);

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.RUNNING.name());
        node.setErrorMessage(null);
        workflowMapper.updateNodeInstance(node);
        touchWorkItem(instance.getWorkItemId(), now);

        scheduleRunNode(instance.getId(), node.getId(), supplementalInput);

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public WorkflowInstanceDto getWorkflowInstance(String instanceId) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Workflow instance not found: " + instanceId);
        }
        return toInstanceDto(instance);
    }

    private void runNode(WorkflowInstanceEntity instance,
                          WorkflowNodeInstanceEntity node,
                          List<WorkflowNodeDefinitionEntity> nodeDefs,
                          WorkItemEntity workItem) {
        runNode(instance, node, nodeDefs, workItem, null);
    }

    private void runNode(WorkflowInstanceEntity instance,
                         WorkflowNodeInstanceEntity node,
                         List<WorkflowNodeDefinitionEntity> nodeDefs,
                         WorkItemEntity workItem,
                         String supplementalInput) {
        if (isWorkflowSuperseded(instance.getId())) {
            return;
        }
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        prepareWorkflowSession(instance, node, nodeDefs, workItem, now);

        executeSkillOnNode(instance, node, nodeDefs, workItem, supplementalInput);

        if (WorkflowNodeStatus.COMPLETED.name().equals(node.getStatus())
                && !isWorkflowPaused(instance.getId())) {
            advanceToNextNode(instance);
        }
    }

    private void runNodeWithFailureGuard(WorkflowInstanceEntity instance,
                                         WorkflowNodeInstanceEntity node,
                                         List<WorkflowNodeDefinitionEntity> nodeDefs,
                                         WorkItemEntity workItem,
                                         String supplementalInput) {
        try {
            runNode(instance, node, nodeDefs, workItem, supplementalInput);
        } catch (Exception e) {
            log.error("Workflow node execution failed: instance={}, node={}",
                    instance != null ? instance.getId() : null,
                    node != null ? node.getId() : null,
                    e);
            if (node != null) {
                markNodeFailed(node.getId(), e);
            }
        }
    }

    private AgentSessionDto prepareWorkflowSession(WorkflowInstanceEntity instance,
                                                    WorkflowNodeInstanceEntity node,
                                                    List<WorkflowNodeDefinitionEntity> nodeDefs,
                                                    WorkItemEntity workItem,
                                                    String now) {
        node.setStatus(WorkflowNodeStatus.RUNNING.name());
        if (node.getStartedAt() == null || node.getStartedAt().isBlank()) {
            node.setStartedAt(now);
        }

        instance.setCurrentNodeInstanceId(node.getId());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        workItem.setCurrentWorkflowInstanceId(instance.getId());
        workItem.setUpdatedAt(now);
        workItemMapper.update(workItem);

        AgentSessionDto session = findWorkflowSession(instance);
        String runtimeSessionId = findWorkflowRuntimeSessionId(instance);

        if (session == null) {
            session = agentSessionService.createSession(
                    SessionType.WORK_ITEM,
                    workItem.getType() + " · " + workItem.getTitle(),
                    workItem.getId(),
                    instance.getId(),
                    workflowRuntimeType,
                    null
            );
        }

        String runtimeSetupError = null;
        try {
            String ensuredRuntimeSessionId = runtimeGateway.ensureSession(
                    workflowRuntimeType, workItem.getId(), session.id(), runtimeSessionId);
            if (!ensuredRuntimeSessionId.equals(runtimeSessionId)) {
                runtimeSessionId = ensuredRuntimeSessionId;
                agentSessionService.bindRuntimeSession(session.id(), runtimeSessionId, workflowRuntimeType);
            }
            node.setErrorMessage(null);
        } catch (Exception e) {
            runtimeSetupError = runtimeSetupFailureMessage(e);
            log.warn("Runtime session is not ready for workflow node {}, session {}. Workflow context will remain visible.",
                    node.getId(), session.id(), e);
        }

        node.setAgentSessionId(session.id());
        node.setRuntimeSessionId(runtimeSessionId);
        if (runtimeSetupError != null) {
            node.setErrorMessage(runtimeSetupError);
        }
        workflowMapper.updateNodeInstance(node);

        runtimeGateway.registerWorkflowNodeContext(
                workflowRuntimeType, session.id(), workItem.getId(), instance.getId(), node.getId());

        return session;
    }

    private void executeSkillOnNode(WorkflowInstanceEntity instance,
                                     WorkflowNodeInstanceEntity node,
                                     List<WorkflowNodeDefinitionEntity> nodeDefs,
                                     WorkItemEntity workItem) {
        executeSkillOnNode(instance, node, nodeDefs, workItem, null);
    }

    private void executeSkillOnNode(WorkflowInstanceEntity instance,
                                    WorkflowNodeInstanceEntity node,
                                    List<WorkflowNodeDefinitionEntity> nodeDefs,
                                    WorkItemEntity workItem,
                                    String supplementalInput) {
        if (isWorkflowSuperseded(instance.getId())) {
            return;
        }
        WorkflowNodeDefinitionEntity nodeDef = nodeDefs.stream()
                .filter(d -> d.getId().equals(node.getNodeDefinitionId()))
                .findFirst().orElseThrow();

        WorkflowContextAnchorService.ContextAnchorDecision contextAnchor =
                workflowContextAnchorService.decide(node.getAgentSessionId(), node.getId());
        String inputContext = buildInputContext(
                workItem,
                node,
                nodeDef,
                supplementalInput,
                workflowContextAnchorService.inputSection(contextAnchor));
        String skillName = nodeDef.getSkillName();
        List<ConfirmationRequestEntity> pendingBeforeRun = findPendingConfirmations(workItem.getId(), node.getId());
        WorkflowResumeState resumeState = buildResumeState(
                instance, node, nodeDefs, nodeDef, workItem, skillName, pendingBeforeRun, idGenerator.nextId());
        String toolCallId = workflowToolCallId(node, skillName);
        insertWorkflowContextMessageIfAbsent(node.getAgentSessionId(), node, nodeDef, workItem, inputContext);
        workflowContextAnchorService.publishContextAnchor(
                contextAnchor, node.getAgentSessionId(), workItem.getId(), instance.getId(), node.getId());

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_STARTED, RuntimeEventSource.WORKFLOW,
                buildSkillStartedPayload(skillName, toolCallId), null
        ));

        Map<Path, FileArtifactSnapshot> artifactFilesBeforeRun = snapshotArtifactFiles(workItem);
        SkillRunResult result = validateSkillRunnable(workItem, skillName);
        if (result == null) {
            if (node.getRuntimeSessionId() == null || node.getRuntimeSessionId().isBlank()) {
                String detail = node.getErrorMessage() != null && !node.getErrorMessage().isBlank()
                        ? " Underlying error: " + node.getErrorMessage()
                        : "";
                result = new SkillRunResult(false, null, null,
                        "Runtime session is not ready. Please check opencode serve and retry this node." + detail,
                        false);
            } else {
                SkillInvocationRequest request = workflowPromptComposer.composeInvocationRequest(skillName, inputContext, resumeState);
                result = runtimeGateway.runSkill(
                        workflowRuntimeType, node.getRuntimeSessionId(), request);
            }
        }
        if (isWorkflowSuperseded(instance.getId())) {
            log.info("Workflow node result ignored because instance was superseded: instance={}, node={}",
                    instance.getId(), node.getId());
            return;
        }

        WorkflowNodeState nodeState = result.success()
                ? WorkflowNodeStateParser.parse(result.outputContent())
                : WorkflowNodeState.defaultInProgress();
        WorkflowTransitionDecision transitionDecision = result.success()
                ? workflowNodeTransitionGuard.decide(instance, node, nodeState, result, resumeState)
                : WorkflowTransitionDecision.accept(nodeState);
        WorkflowNodeState effectiveNodeState = transitionDecision.effectiveState();
        if (transitionDecision.rejected()) {
            log.warn("Workflow node transition rejected: instance={}, node={}, reportedState={}, decision={}, reason={}",
                    instance.getId(), node.getId(), nodeState.getStatus(), transitionDecision.type(), transitionDecision.reason());
        }

        ArtifactEntity artifact = null;
        if (result.success() && transitionDecision.acceptsReady()) {
            artifact = resolveNodeOutputFileArtifact(instance, node, workItem, effectiveNodeState, artifactFilesBeforeRun);
            if (artifact == null) {
                artifact = materializeNodeOutputFileArtifact(instance, node, workItem, effectiveNodeState, result);
            }
            if (artifact != null) {
                node.setOutputArtifactId(artifact.getId());
            }
        }

        if (artifact == null && node.getOutputArtifactId() != null && !node.getOutputArtifactId().isBlank()) {
            artifact = artifactMapper.findById(node.getOutputArtifactId());
        } else if (artifact != null) {
            node.setOutputArtifactId(artifact.getId());
        }

        String sessionId = node.getAgentSessionId();
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        AgentMessageEntity statusMsg = new AgentMessageEntity();
        statusMsg.setId(idGenerator.nextId());
        statusMsg.setSessionId(sessionId);
        statusMsg.setRole(MessageRole.SYSTEM.name());
        statusMsg.setContent(buildNodeStatusMessage(
                result, nodeDef, skillName, artifact, effectiveNodeState));
        statusMsg.setContentFormat(ContentFormat.TEXT.name());
        statusMsg.setStatus(MessageStatus.COMPLETED.name());
        statusMsg.setCreatedBy("workflow-engine");
        statusMsg.setCreatedAt(now);
        agentMessageWriteService.insertWithNextSeqNo(statusMsg);

        List<ConfirmationRequestEntity> pendingConfirmations = new ArrayList<>();

        if (!result.success()) {
            node.setStatus(WorkflowNodeStatus.FAILED.name());
            node.setErrorMessage(result.errorMessage());
            node.setCompletedAt(now);
            workflowMapper.updateNodeInstance(node);
            createFailureConfirmation(instance, node, nodeDef, workItem, skillName, result.errorMessage(), now);
        } else {
            node.setAgentStatePayloadJson(buildAgentStatePayloadJson(resumeState, transitionDecision, nodeState, effectiveNodeState));
            switch (effectiveNodeState.getStatus()) {
                case READY_TO_ADVANCE -> {
                    node.setAgentState(effectiveNodeState.getStatus().name());
                    node.setAgentStateReason(effectiveNodeState.getReason());
                    node.setAgentStateUpdatedAt(now);
                    if (effectiveNodeState.getArtifactTitle() != null) {
                        node.setAgentStateArtifactTitle(effectiveNodeState.getArtifactTitle());
                    }

                    if (isAutoRun(instance)) {
                        node.setStatus(WorkflowNodeStatus.COMPLETED.name());
                        node.setCompletedAt(now);
                        if (!isWorkflowPaused(instance.getId())) {
                            instance.setStatus(WorkflowStatus.RUNNING.name());
                            instance.setUpdatedAt(now);
                            workflowMapper.updateInstance(instance);
                        }
                    } else {
                        ConfirmationRequestEntity advanceConfirm = buildAdvanceConfirmation(
                                instance, node, nodeDef, workItem, now);
                        confirmationMapper.insert(advanceConfirm);
                        pendingConfirmations.add(advanceConfirm);

                        node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());
                        markInstanceBlocked(instance, now);
                    }
                }
                case NEEDS_USER_INPUT -> {
                    if (transitionDecision.reusesPendingInteraction()) {
                        node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());

                        node.setAgentState(effectiveNodeState.getStatus().name());
                        node.setAgentStateReason(effectiveNodeState.getReason());
                        node.setAgentStateUpdatedAt(now);

                        markInstanceBlocked(instance, now);
                    } else if (effectiveNodeState.getInteractions() != null && !effectiveNodeState.getInteractions().isEmpty()) {
                        int orderNo = 0;
                        for (WorkflowNodeInteraction interaction : effectiveNodeState.getInteractions()) {
                            ConfirmationRequestEntity confirmation = interactionMapper.toEntity(
                                    interaction, workItem.getId(), instance.getId(), node.getId(),
                                    node.getAgentSessionId(), workflowRuntimeType.name(),
                                    node.getRuntimeSessionId(), skillName);
                            confirmation.setId(idGenerator.nextId());
                            confirmation.setInteractionOrderNo(orderNo++);
                            confirmation.setCreatedAt(now);
                            confirmation.setUpdatedAt(now);
                            confirmationMapper.insert(confirmation);
                            pendingConfirmations.add(confirmation);
                        }
                    } else {
                        ConfirmationRequestEntity inputRequest = buildGenericInputConfirmation(
                                instance, node, nodeDef, workItem, skillName,
                                effectiveNodeState.getReason(), now);
                        confirmationMapper.insert(inputRequest);
                        pendingConfirmations.add(inputRequest);
                    }
                    node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());

                    node.setAgentState(effectiveNodeState.getStatus().name());
                    node.setAgentStateReason(effectiveNodeState.getReason());
                    node.setAgentStateUpdatedAt(now);

                    markInstanceBlocked(instance, now);
                }
                case BLOCKED -> {
                    createFailureConfirmation(instance, node, nodeDef, workItem, skillName,
                            effectiveNodeState.getReason(), now);
                    node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());

                    node.setAgentState(effectiveNodeState.getStatus().name());
                    node.setAgentStateReason(effectiveNodeState.getReason());
                    node.setAgentStateUpdatedAt(now);

                    markInstanceBlocked(instance, now);
                }
                case IN_PROGRESS -> {
                    // Stays RUNNING — no artifact, no advance
                    node.setAgentState(effectiveNodeState.getStatus().name());
                    node.setAgentStateReason(effectiveNodeState.getReason());
                    node.setAgentStateUpdatedAt(now);
                }
            }
        }

        if (!node.getStatus().equals(WorkflowNodeStatus.FAILED.name())) {
            workflowMapper.updateNodeInstance(node);
        }
        touchWorkItem(workItem.getId(), now);

        String statePayload = effectiveNodeState.getStatus().name();
        String stateReason = effectiveNodeState.getReason() != null ? effectiveNodeState.getReason() : "";
        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_COMPLETED, RuntimeEventSource.WORKFLOW,
                buildSkillCompletedPayload(skillName, toolCallId, result, statePayload, stateReason, artifact),
                null
        ));

        for (ConfirmationRequestEntity confirmation : pendingConfirmations) {
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null, node.getAgentSessionId(), workItem.getId(),
                    instance.getId(), node.getId(),
                    RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                    confirmationCreatedPayloadBuilder.buildPayload(confirmation), null
            ));
        }
    }

    private List<ConfirmationRequestEntity> findPendingConfirmations(String workItemId, String nodeInstanceId) {
        return confirmationMapper.findByWorkItemId(workItemId).stream()
                .filter(confirmation -> nodeInstanceId.equals(confirmation.getWorkflowNodeInstanceId()))
                .filter(confirmation -> ConfirmationStatus.PENDING.name().equals(confirmation.getStatus())
                        || ConfirmationStatus.IN_CONVERSATION.name().equals(confirmation.getStatus()))
                .toList();
    }

    private WorkflowResumeState buildResumeState(WorkflowInstanceEntity instance,
                                                 WorkflowNodeInstanceEntity currentNode,
                                                 List<WorkflowNodeDefinitionEntity> nodeDefs,
                                                 WorkflowNodeDefinitionEntity currentNodeDef,
                                                 WorkItemEntity workItem,
                                                 String skillName,
                                                 List<ConfirmationRequestEntity> pendingInteractions,
                                                 String invocationId) {
        Map<String, WorkflowNodeInstanceEntity> nodeByDefinitionId = workflowMapper
                .findNodeInstancesByWorkflowInstanceId(instance.getId()).stream()
                .collect(Collectors.toMap(
                        WorkflowNodeInstanceEntity::getNodeDefinitionId,
                        node -> node,
                        (left, ignored) -> left));

        List<WorkflowResumeState.WorkflowStep> steps = nodeDefs.stream()
                .sorted(Comparator.comparingInt(WorkflowNodeDefinitionEntity::getOrderNo))
                .map(definition -> {
                    WorkflowNodeInstanceEntity node = nodeByDefinitionId.get(definition.getId());
                    boolean current = currentNode.getId().equals(node != null ? node.getId() : null);
                    return new WorkflowResumeState.WorkflowStep(
                            node != null ? node.getId() : null,
                            definition.getId(),
                            definition.getNodeKey(),
                            definition.getName(),
                            definition.getOrderNo(),
                            definition.getSkillName(),
                            definition.getStageKey(),
                            node != null ? node.getStatus() : WorkflowNodeStatus.PENDING.name(),
                            current);
                })
                .toList();

        List<WorkflowResumeState.PendingInteraction> pending = pendingInteractions.stream()
                .map(confirmation -> new WorkflowResumeState.PendingInteraction(
                        confirmation.getId(),
                        nonBlank(confirmation.getInteractionType(), confirmation.getRequestType()),
                        confirmation.getTitle()))
                .toList();

        String currentGate = pending.isEmpty() ? "NODE_EXECUTION" : "PENDING_USER_INTERACTION";
        return new WorkflowResumeState(
                instance.getId(),
                currentNode.getId(),
                workItem.getId(),
                workItem.getProjectId(),
                currentNode.getRuntimeSessionId(),
                currentGate,
                currentNode.getStatus(),
                skillName != null ? skillName : currentNodeDef.getSkillName(),
                invocationId,
                steps,
                pending);
    }

    private String buildAgentStatePayloadJson(WorkflowResumeState resumeState,
                                              WorkflowTransitionDecision decision,
                                              WorkflowNodeState reportedState,
                                              WorkflowNodeState effectiveState) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendJsonStringField(sb, "invocationId", resumeState.invocationId());
        appendJsonStringField(sb, "currentGate", resumeState.currentGate());
        appendJsonStringField(sb, "reportedState", reportedState.getStatus().name());
        appendJsonStringField(sb, "effectiveState", effectiveState.getStatus().name());
        appendJsonStringField(sb, "transitionDecision", decision.type().name());
        appendJsonStringField(sb, "transitionReason", decision.reason());
        sb.append(",\"pendingInteractionIds\":[");
        for (int i = 0; i < resumeState.pendingInteractions().size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(nonBlank(resumeState.pendingInteractions().get(i).id(), ""))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void appendJsonStringField(StringBuilder sb, String key, String value) {
        if (sb.length() > 1) {
            sb.append(",");
        }
        sb.append("\"").append(key).append("\":\"")
                .append(escapeJson(value != null ? value : ""))
                .append("\"");
    }

    private ConfirmationRequestEntity buildAdvanceConfirmation(WorkflowInstanceEntity instance,
                                                               WorkflowNodeInstanceEntity node,
                                                               WorkflowNodeDefinitionEntity nodeDef,
                                                               WorkItemEntity workItem,
                                                               String now) {
        String advanceTitle = "%s %s · 等待推进确认".formatted(
                workItem != null ? workItem.getCode() : "",
                nodeDef.getName());
        String advanceOptionsJson = "[{\"id\":\"ADVANCE\",\"label\":\"进入下一节点\"},{\"id\":\"SUPPLEMENT\",\"label\":\"继续补充当前节点\"},{\"id\":\"RETRY\",\"label\":\"重新执行\"}]";

        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(idGenerator.nextId());
        confirmation.setRequestType(ConfirmationRequestType.DECISION.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setWorkItemId(instance.getWorkItemId());
        confirmation.setWorkflowInstanceId(instance.getId());
        confirmation.setWorkflowNodeInstanceId(node.getId());
        confirmation.setAgentSessionId(node.getAgentSessionId());
        confirmation.setRuntimeType(workflowRuntimeType.name());
        confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
        confirmation.setSkillName(nodeDef.getSkillName());
        confirmation.setTitle(advanceTitle);
        confirmation.setContent("当前节点已完成，请选择下一步操作。");
        confirmation.setOptionsJson(advanceOptionsJson);
        confirmation.setInteractionType("WORKFLOW_ADVANCE");
        confirmation.setPriority(Priority.MEDIUM.name());
        confirmation.setInteractionRequired(1);
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        return confirmation;
    }

    private void createFailureConfirmation(WorkflowInstanceEntity instance,
                                           WorkflowNodeInstanceEntity node,
                                           WorkflowNodeDefinitionEntity nodeDef,
                                           WorkItemEntity workItem,
                                           String skillName,
                                           String errorMessage,
                                           String now) {
        String detail = nonBlank(errorMessage, "节点执行失败，Runtime 未返回明确错误原因。");
        ConfirmationRequestEntity existing = confirmationMapper.findByWorkItemId(workItem.getId()).stream()
                .filter(confirmation ->
                        node.getId().equals(confirmation.getWorkflowNodeInstanceId())
                                && ConfirmationRequestType.EXCEPTION.name().equals(confirmation.getRequestType())
                                && (ConfirmationStatus.PENDING.name().equals(confirmation.getStatus())
                                || ConfirmationStatus.IN_CONVERSATION.name().equals(confirmation.getStatus())))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setAgentSessionId(node.getAgentSessionId());
            existing.setRuntimeType(workflowRuntimeType.name());
            existing.setRuntimeSessionId(node.getRuntimeSessionId());
            existing.setSkillName(skillName);
            existing.setContent(buildFailureConfirmationContent(detail));
            existing.setContextSummary("工作流节点 %s（Skill：%s）执行失败，流程已暂停等待处理。"
                    .formatted(nodeDef.getName(), skillName));
            existing.setUpdatedAt(now);
            confirmationMapper.update(existing);
            markInstanceBlocked(instance, now);
            return;
        }

        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(idGenerator.nextId());
        confirmation.setRequestType(ConfirmationRequestType.EXCEPTION.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setProjectId(workItem.getProjectId());
        confirmation.setSpaceId(workItem.getSpaceId());
        confirmation.setIterationId(workItem.getIterationId());
        confirmation.setWorkItemId(workItem.getId());
        confirmation.setWorkflowInstanceId(instance.getId());
        confirmation.setWorkflowNodeInstanceId(node.getId());
        confirmation.setAgentSessionId(node.getAgentSessionId());
        confirmation.setRuntimeType(workflowRuntimeType.name());
        confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
        confirmation.setSkillName(skillName);
        confirmation.setTitle("%s %s · 执行异常".formatted(workItem.getCode(), nodeDef.getName()));
        confirmation.setContent(buildFailureConfirmationContent(detail));
        confirmation.setContextSummary("工作流节点 %s（Skill：%s）执行失败，流程已暂停等待处理。"
                .formatted(nodeDef.getName(), skillName));
        confirmation.setOptionsJson("[{\"value\":\"RETRY\",\"label\":\"重试当前节点\"},"
                + "{\"value\":\"SUPPLEMENT\",\"label\":\"补充信息后继续\"},"
                + "{\"value\":\"SKIP\",\"label\":\"跳过该节点继续\"}]");
        confirmation.setPriority(Priority.HIGH.name());
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.insert(confirmation);

        markInstanceBlocked(instance, now);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                confirmationCreatedPayloadBuilder.buildPayload(confirmation), null
        ));
    }

    private String buildFailureConfirmationContent(String detail) {
        return """
                节点执行失败，需要用户决定后续处理方式。

                失败原因：%s

                可选择重试当前节点；也可以补充信息后继续当前节点。如果确认该节点暂时不阻塞后续流程，也可以跳过。
                """.formatted(detail).trim();
    }

    private void markInstanceBlocked(WorkflowInstanceEntity instance, String now) {
        if (isWorkflowPaused(instance.getId())) {
            return;
        }
        instance.setStatus(WorkflowStatus.BLOCKED.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);
    }

    private ArtifactEntity resolveNodeOutputFileArtifact(WorkflowInstanceEntity instance,
                                                         WorkflowNodeInstanceEntity node,
                                                         WorkItemEntity workItem,
                                                         WorkflowNodeState nodeState,
                                                         Map<Path, FileArtifactSnapshot> beforeRun) {
        ArtifactEntity existing = latestFileBackedArtifactForNode(node.getId(), workItem);
        if (existing != null) {
            return existing;
        }

        List<ArtifactEntity> captured = captureChangedFileArtifacts(instance, node, workItem, nodeState, beforeRun);
        return captured.stream()
                .filter(artifact -> isReadableFileBackedArtifact(artifact, workItem))
                .findFirst()
                .orElse(null);
    }

    private ArtifactEntity latestFileBackedArtifactForNode(String nodeInstanceId, WorkItemEntity workItem) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return null;
        }
        return artifactMapper.findByWorkflowNodeInstanceId(nodeInstanceId).stream()
                .filter(artifact -> isReadableFileBackedArtifact(artifact, workItem))
                .findFirst()
                .orElse(null);
    }

    private List<ArtifactEntity> captureChangedFileArtifacts(WorkflowInstanceEntity instance,
                                                             WorkflowNodeInstanceEntity node,
                                                             WorkItemEntity workItem,
                                                             WorkflowNodeState nodeState,
                                                             Map<Path, FileArtifactSnapshot> beforeRun) {
        Map<Path, FileArtifactSnapshot> afterRun = snapshotArtifactFiles(workItem);
        if (afterRun.isEmpty()) {
            return List.of();
        }

        Map<Path, ArtifactEntity> existingByPath = new HashMap<>();
        artifactMapper.findByWorkflowNodeInstanceId(node.getId()).stream()
                .filter(artifact -> firstNonBlank(artifact.getFilePath(), artifact.getStorageUri()) != null)
                .forEach(artifact -> existingByPath.put(normalizedArtifactPath(artifact), artifact));

        List<ArtifactEntity> captured = new ArrayList<>();
        afterRun.values().stream()
                .filter(snapshot -> isNewOrChanged(snapshot, beforeRun.get(snapshot.path())))
                .sorted(Comparator.comparingLong(FileArtifactSnapshot::modifiedMillis).reversed())
                .forEach(snapshot -> {
                    ArtifactEntity existing = existingByPath.get(snapshot.path());
                    if (existing != null) {
                        captured.add(existing);
                        return;
                    }
                    ArtifactEntity artifact = createFileSnapshotArtifact(instance, node, workItem, snapshot, nodeState);
                    artifactMapper.insert(artifact);
                    captured.add(artifact);
                });
        return captured;
    }

    private ArtifactEntity createFileSnapshotArtifact(WorkflowInstanceEntity instance,
                                                      WorkflowNodeInstanceEntity node,
                                                      WorkItemEntity workItem,
                                                      FileArtifactSnapshot snapshot) {
        return createFileSnapshotArtifact(instance, node, workItem, snapshot, null);
    }

    private ArtifactEntity createFileSnapshotArtifact(WorkflowInstanceEntity instance,
                                                      WorkflowNodeInstanceEntity node,
                                                      WorkItemEntity workItem,
                                                      FileArtifactSnapshot snapshot,
                                                      WorkflowNodeState nodeState) {
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(idGenerator.nextId());
        artifact.setWorkItemId(workItem.getId());
        artifact.setWorkflowInstanceId(instance.getId());
        artifact.setWorkflowNodeInstanceId(node.getId());
        artifact.setSessionId(node.getAgentSessionId());
        artifact.setArtifactType(artifactTypeFromPath(snapshot.path()).name());
        artifact.setTitle(fileArtifactTitle(snapshot.path(), nodeState));
        artifact.setContent(null);
        artifact.setStorageUri(snapshot.path().toString());
        artifact.setFilePath(snapshot.path().toString());
        artifact.setVersionNo(1);
        artifact.setSourceType("FILE_SNAPSHOT");
        artifact.setCreatedBy("workflow-engine");
        artifact.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        return artifact;
    }

    private String fileArtifactTitle(Path path, WorkflowNodeState nodeState) {
        if (nodeState != null && nodeState.getArtifactTitle() != null && !nodeState.getArtifactTitle().isBlank()) {
            return nodeState.getArtifactTitle().trim();
        }
        String documentTitle = markdownDocumentTitle(path);
        if (documentTitle != null && !documentTitle.isBlank()) {
            return documentTitle.trim() + extensionFromPath(path);
        }
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    private String markdownDocumentTitle(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        if (!artifactTypeFromPath(path).equals(ArtifactType.MARKDOWN)) {
            return null;
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("# ") && line.length() > 2)
                    .map(line -> line.substring(2).trim())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.debug("Failed to read artifact title from {}: {}", path, e.getMessage());
            return null;
        }
    }

    private String extensionFromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private ArtifactEntity materializeNodeOutputFileArtifact(WorkflowInstanceEntity instance,
                                                             WorkflowNodeInstanceEntity node,
                                                             WorkItemEntity workItem,
                                                             WorkflowNodeState nodeState,
                                                             SkillRunResult result) {
        if (result == null || result.outputContent() == null || result.outputContent().isBlank()) {
            return null;
        }
        if (nodeState == null || nodeState.getArtifactTitle() == null || nodeState.getArtifactTitle().isBlank()) {
            return null;
        }
        String content = WorkflowNodeStateParser.stripStateBlock(result.outputContent());
        if (content == null || content.isBlank()) {
            return null;
        }

        String title = nodeState.getArtifactTitle().trim();
        Path workspace = runtimeWorkspaceForWorkItem(workItem).toAbsolutePath().normalize();
        Path artifactFile = workspace.resolve("artifacts").resolve(safeArtifactFileName(title))
                .toAbsolutePath().normalize();
        if (!artifactFile.startsWith(workspace)) {
            return null;
        }

        try {
            Files.createDirectories(artifactFile.getParent());
            Files.writeString(artifactFile, content, StandardCharsets.UTF_8);
            ArtifactEntity artifact = createFileSnapshotArtifact(
                    instance,
                    node,
                    workItem,
                    new FileArtifactSnapshot(
                            artifactFile,
                            Files.getLastModifiedTime(artifactFile).toMillis(),
                            Files.size(artifactFile)));
            artifact.setTitle(title);
            artifactMapper.insert(artifact);
            return artifact;
        } catch (IOException e) {
            log.warn("Failed to materialize workflow node artifact file for node {}: {}", node.getId(), e.getMessage());
            return null;
        }
    }

    private String safeArtifactFileName(String title) {
        String name = title == null ? "" : title.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-")
                .replaceAll("\\s+", " ")
                .trim();
        if (name.isBlank()) {
            name = "workflow-artifact.md";
        }
        String lower = name.toLowerCase();
        if (ARTIFACT_FILE_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            name = name + ".md";
        }
        return name;
    }

    private Map<Path, FileArtifactSnapshot> snapshotArtifactFiles(WorkItemEntity workItem) {
        Path root = runtimeWorkspaceForWorkItem(workItem);
        if (!Files.isDirectory(root)) {
            return Map.of();
        }
        Map<Path, FileArtifactSnapshot> snapshots = new HashMap<>();
        try {
            Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    ARTIFACT_SCAN_MAX_DEPTH, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (!root.equals(dir) && shouldSkipArtifactScanDirectory(dir)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            Path normalized = file.toAbsolutePath().normalize();
                            if (isArtifactCandidateFile(normalized, attrs)) {
                                snapshots.put(normalized, new FileArtifactSnapshot(
                                        normalized,
                                        attrs.lastModifiedTime().toMillis(),
                                        attrs.size()));
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.debug("Failed to scan runtime workspace artifacts: {}", e.getMessage());
        }
        return snapshots;
    }

    private boolean shouldSkipArtifactScanDirectory(Path dir) {
        Path fileName = dir.getFileName();
        String name = fileName != null ? fileName.toString().toLowerCase() : "";
        return ARTIFACT_SCAN_EXCLUDED_DIRECTORIES.contains(name);
    }

    private boolean isArtifactCandidateFile(Path file, BasicFileAttributes attrs) {
        if (attrs == null || !attrs.isRegularFile() || attrs.size() > MAX_INLINE_ARTIFACT_BYTES) {
            return false;
        }
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        return ARTIFACT_FILE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean isNewOrChanged(FileArtifactSnapshot after, FileArtifactSnapshot before) {
        return before == null
                || after.modifiedMillis() != before.modifiedMillis()
                || after.size() != before.size();
    }

    private ArtifactType artifactTypeFromPath(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        if (name.endsWith(".json")) {
            return ArtifactType.JSON;
        }
        if (name.endsWith(".patch") || name.endsWith(".diff")) {
            return ArtifactType.PATCH;
        }
        if (name.endsWith(".txt")) {
            return ArtifactType.REPORT;
        }
        return ArtifactType.MARKDOWN;
    }

    private boolean isReadableFileBackedArtifact(ArtifactEntity artifact, WorkItemEntity workItem) {
        Path path = normalizedArtifactPath(artifact);
        if (path == null) {
            return false;
        }
        Path workspace = runtimeWorkspaceForWorkItem(workItem).toAbsolutePath().normalize();
        return path.startsWith(workspace)
                && Files.isRegularFile(path)
                && isInlineArtifactPath(path)
                && safeFileSize(path) <= MAX_INLINE_ARTIFACT_BYTES;
    }

    private String artifactContentForPrompt(ArtifactEntity artifact) {
        if (artifact == null) {
            return null;
        }
        if (artifact.getContent() != null && !artifact.getContent().isBlank()) {
            return artifact.getContent();
        }
        Path path = normalizedArtifactPath(artifact);
        if (path == null) {
            return null;
        }
        WorkItemEntity workItem = artifact.getWorkItemId() != null
                ? workItemMapper.findById(artifact.getWorkItemId())
                : null;
        if (!isReadableFileBackedArtifact(artifact, workItem)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Failed to read artifact file {} for prompt context: {}", path, e.getMessage());
            return null;
        }
    }

    private Path normalizedArtifactPath(ArtifactEntity artifact) {
        String value = firstNonBlank(artifact.getFilePath(), artifact.getStorageUri());
        return value == null ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private Path runtimeWorkspaceForWorkItem(WorkItemEntity workItem) {
        String projectId = workItem != null ? workItem.getProjectId() : null;
        return workspaceResolver.resolve(projectId).toAbsolutePath().normalize();
    }

    private boolean isInlineArtifactPath(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        return ARTIFACT_FILE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private long safeFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private ConfirmationRequestEntity buildFallbackApprovalConfirmation(
            WorkflowInstanceEntity instance,
            WorkflowNodeInstanceEntity node,
            WorkflowNodeDefinitionEntity nodeDef,
            WorkItemEntity workItem,
            String skillName,
            ArtifactEntity artifact,
            String now) {
        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(idGenerator.nextId());
        confirmation.setRequestType(ConfirmationRequestType.APPROVAL.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setProjectId(workItem.getProjectId());
        confirmation.setSpaceId(workItem.getSpaceId());
        confirmation.setIterationId(workItem.getIterationId());
        confirmation.setWorkItemId(workItem.getId());
        confirmation.setWorkflowInstanceId(instance.getId());
        confirmation.setWorkflowNodeInstanceId(node.getId());
        confirmation.setAgentSessionId(node.getAgentSessionId());
        confirmation.setRuntimeType(workflowRuntimeType.name());
        confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
        confirmation.setSkillName(skillName);
        confirmation.setTitle("%s %s · 确认审批".formatted(workItem.getCode(), nodeDef.getName()));
        confirmation.setContent("节点 %s（Skill：%s）执行完成，需要确认后继续。"
                .formatted(nodeDef.getName(), skillName));
        StringBuilder summary = new StringBuilder();
        summary.append("工作流节点 %s（Skill：%s）标记为需要确认，自动创建审批。"
                .formatted(nodeDef.getName(), skillName));
        if (artifact != null) {
            summary.append("关联产物：").append(artifact.getTitle()).append("。");
        }
        confirmation.setContextSummary(summary.toString());
        confirmation.setOptionsJson("[\"确认通过\",\"驳回\"]");
        confirmation.setPriority(Priority.MEDIUM.name());
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        return confirmation;
    }

    private ConfirmationRequestEntity buildGenericInputConfirmation(
            WorkflowInstanceEntity instance,
            WorkflowNodeInstanceEntity node,
            WorkflowNodeDefinitionEntity nodeDef,
            WorkItemEntity workItem,
            String skillName,
            String reason,
            String now) {
        ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
        confirmation.setId(idGenerator.nextId());
        boolean choiceLike = looksLikeChoiceRequest(reason);
        confirmation.setRequestType(choiceLike
                ? ConfirmationRequestType.DECISION.name()
                : ConfirmationRequestType.INPUT_REQUIRED.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setWorkItemId(workItem.getId());
        confirmation.setWorkflowInstanceId(instance.getId());
        confirmation.setWorkflowNodeInstanceId(node.getId());
        confirmation.setAgentSessionId(node.getAgentSessionId());
        confirmation.setRuntimeType(workflowRuntimeType.name());
        confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
        confirmation.setSkillName(skillName);
        confirmation.setTitle("%s %s · %s".formatted(
                workItem.getCode(),
                nodeDef.getName(),
                choiceLike ? "需要选择" : "需要补充输入"));
        confirmation.setContent(nonBlank(reason, "Agent 需要用户补充信息"));
        confirmation.setContextSummary(reason);
        confirmation.setInteractionType(choiceLike ? "DECISION" : "INPUT");
        if (choiceLike) {
            confirmation.setOptionsJson(FALLBACK_DECISION_OPTIONS_JSON);
            confirmation.setInteractionSchemaJson(fallbackDecisionSchemaJson(confirmation.getTitle(), confirmation.getContent()));
        }
        confirmation.setInteractionRequired(1);
        confirmation.setPriority("MEDIUM");
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        return confirmation;
    }

    private boolean looksLikeChoiceRequest(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String text = reason.toLowerCase();
        return text.contains("选择")
                || text.contains("选项")
                || text.contains("方案")
                || text.contains("路线")
                || text.contains("策略")
                || text.contains("取舍")
                || text.contains("是否")
                || text.contains("审批")
                || text.contains("审阅")
                || text.contains("通过")
                || text.contains("驳回")
                || text.contains("decision")
                || text.contains("choose")
                || text.contains("select")
                || text.contains("option")
                || text.contains("review")
                || text.contains("approve");
    }

    private String fallbackDecisionSchemaJson(String title, String question) {
        return "{"
                + "\"type\":\"DECISION\","
                + "\"title\":\"" + escapeJson(nonBlank(title, "需要选择")) + "\","
                + "\"question\":\"" + escapeJson(nonBlank(question, "请选择下一步处理方式")) + "\","
                + "\"selection\":\"single\","
                + "\"options\":" + FALLBACK_DECISION_OPTIONS_JSON + ","
                + "\"allowCustom\":true,"
                + "\"required\":true"
                + "}";
    }

    private SkillRunResult validateSkillRunnable(WorkItemEntity workItem, String skillName) {
        String validationError = skillRegistryService.validateRunnableSkill(workItem.getProjectId(), skillName);
        if (validationError != null) {
            return new SkillRunResult(false, null, null,
                    validationError, false);
        }
        return null;
    }

    private void validateDefinitionSkillsRunnable(WorkItemEntity workItem, List<WorkflowNodeDefinitionEntity> nodeDefs) {
        skillRegistryService.syncSkillsFromFilesystem(workItem.getProjectId());
        for (WorkflowNodeDefinitionEntity nodeDef : nodeDefs) {
            String validationError = skillRegistryService.validateRegisteredRunnableSkill(
                    workItem.getProjectId(), nodeDef.getSkillName());
            if (validationError != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Workflow definition references unavailable Skill at node "
                                + nodeDef.getName() + ": " + validationError);
            }
        }
    }

    private String workflowToolCallId(WorkflowNodeInstanceEntity node, String skillName) {
        return "workflow:" + node.getId() + ":" + skillName;
    }

    private String buildSkillStartedPayload(String skillName, String toolCallId) {
        return "{\"skillName\":\"" + escapeJson(skillName) + "\",\"toolCallId\":\"" + escapeJson(toolCallId) + "\"}";
    }

    private String buildSkillCompletedPayload(String skillName, String toolCallId, SkillRunResult result, String nodeState, String nodeStateReason, ArtifactEntity artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"skillName\":\"").append(escapeJson(skillName)).append("\"");
        sb.append(",\"toolCallId\":\"").append(escapeJson(toolCallId)).append("\"");
        sb.append(",\"success\":").append(result.success());
        if (artifact != null && artifact.getId() != null && !artifact.getId().isBlank()) {
            String title = artifact.getTitle() == null ? "" : artifact.getTitle();
            sb.append(",\"artifactId\":\"").append(escapeJson(artifact.getId())).append("\"");
            sb.append(",\"artifactTitle\":\"").append(escapeJson(title)).append("\"");
            sb.append(",\"title\":\"").append(escapeJson(title)).append("\"");
        }
        if (!result.success() && result.errorMessage() != null && !result.errorMessage().isBlank()) {
            String errorMessage = result.errorMessage();
            sb.append(",\"isError\":true");
            sb.append(",\"recoverable\":true");
            sb.append(",\"errorCode\":\"").append(escapeJson(errorCodeFor(errorMessage))).append("\"");
            sb.append(",\"errorMessage\":\"").append(escapeJson(errorMessage)).append("\"");
            sb.append(",\"output\":\"").append(escapeJson(errorMessage)).append("\"");
            sb.append(",\"recommendedActions\":[\"RETRY\",\"SUPPLEMENT\",\"SKIP\"]");
        }
        sb.append(",\"nodeState\":\"").append(escapeJson(nodeState)).append("\"");
        if (nodeStateReason != null && !nodeStateReason.isBlank()) {
            sb.append(",\"nodeStateReason\":\"").append(escapeJson(nodeStateReason)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String errorCodeFor(String errorMessage) {
        String normalized = errorMessage == null ? "" : errorMessage.toLowerCase();
        if (normalized.contains("timeout") || normalized.contains("超时") || normalized.contains("没有返回可用输出")) {
            return "RUNTIME_TIMEOUT";
        }
        return "RUNTIME_ERROR";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private AgentSessionDto findWorkflowSession(WorkflowInstanceEntity instance) {
        return workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId()).stream()
                .filter(n -> n.getAgentSessionId() != null)
                .map(n -> {
                    try {
                        return agentSessionService.getSession(n.getAgentSessionId());
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String runtimeSetupFailureMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return error.getClass().getSimpleName() + ": " + message;
        }
        return error.getClass().getSimpleName();
    }

    private String findWorkflowRuntimeSessionId(WorkflowInstanceEntity instance) {
        return workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId()).stream()
                .map(WorkflowNodeInstanceEntity::getRuntimeSessionId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void insertWorkflowContextMessageIfAbsent(String sessionId,
                                                      WorkflowNodeInstanceEntity node,
                                                      WorkflowNodeDefinitionEntity nodeDef,
                                                      WorkItemEntity workItem,
                                                      String inputContext) {
        AgentMessageEntity contextMsg = new AgentMessageEntity();
        contextMsg.setId(idGenerator.nextId());
        contextMsg.setSessionId(sessionId);
        contextMsg.setRole(MessageRole.USER.name());
        contextMsg.setContent("""
                请执行工作流节点：%s

                ## 用户输入

                - 工作项编号：%s
                - 工作项标题：%s
                - 工作项类型：%s
                - 工作项状态：%s
                - 优先级：%s
                - 使用 Skill：%s

                ## 任务信息

                ```text
                %s
                ```

                ## 节点上下文

                ```text
                %s
                ```
                """.formatted(
                nodeDef.getName(),
                workItem.getCode(),
                workItem.getTitle(),
                workItem.getType(),
                workItem.getStatus(),
                workItem.getPriority(),
                nodeDef.getSkillName(),
                nonBlank(workItem.getDescription(), "暂无描述"),
                inputContext
        ).trim());
        contextMsg.setContentFormat(ContentFormat.MARKDOWN.name());
        contextMsg.setStatus(MessageStatus.COMPLETED.name());
        contextMsg.setCreatedBy("workflow-engine");
        contextMsg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        contextMsg.setWorkflowNodeInstanceId(node.getId());
        agentMessageWriteService.insertWithNextSeqNoIfAbsent(
                contextMsg,
                existing -> existing.stream()
                        .anyMatch(message -> MessageRole.USER.name().equals(message.getRole())
                                && node.getId().equals(message.getWorkflowNodeInstanceId())));
    }

    private void insertWorkflowStartContextMessage(String sessionId,
                                                    WorkItemEntity workItem,
                                                    WorkflowDefinitionEntity definition,
                                                    List<WorkflowNodeDefinitionEntity> nodeDefs) {
        String nodeSummary = nodeDefs.stream()
                .sorted(Comparator.comparingInt(WorkflowNodeDefinitionEntity::getOrderNo))
                .map(node -> "%d. %s（Skill：%s，%s）".formatted(
                        node.getOrderNo(),
                        node.getName(),
                        node.getSkillName(),
                        Boolean.TRUE.equals(node.getRequiredConfirmation()) ? "需要确认" : "自动推进"
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未配置节点");

        AgentMessageEntity contextMsg = new AgentMessageEntity();
        contextMsg.setId(idGenerator.nextId());
        contextMsg.setSessionId(sessionId);
        contextMsg.setRole(MessageRole.SYSTEM.name());
        contextMsg.setContent("""
                已创建任务会话，并注入工作流上下文

                - 工作项：%s %s
                - 类型：%s
                - 状态：%s
                - 优先级：%s
                - 工作流：%s v%s

                任务描述：

                ```text
                %s
                ```

                节点计划：

                %s
                """.formatted(
                workItem.getCode(),
                workItem.getTitle(),
                workItem.getType(),
                workItem.getStatus(),
                workItem.getPriority(),
                definition.getName(),
                definition.getVersionNo(),
                workItem.getDescription() != null && !workItem.getDescription().isBlank()
                        ? workItem.getDescription()
                        : "暂无描述",
                nodeSummary
        ).trim());
        contextMsg.setContentFormat(ContentFormat.MARKDOWN.name());
        contextMsg.setStatus(MessageStatus.COMPLETED.name());
        contextMsg.setCreatedBy("workflow-engine");
        contextMsg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        agentMessageWriteService.insertWithNextSeqNo(contextMsg);
    }

    private void scheduleRunNode(String instanceId, String nodeInstanceId) {
        scheduleRunNode(instanceId, nodeInstanceId, null);
    }

    private void scheduleRunNode(String instanceId, String nodeInstanceId, String supplementalInput) {
        workflowExecutor.submit(() -> {
            try {
                WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
                WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
                if (instance == null || node == null) {
                    return;
                }
                if (WorkflowStatus.SUPERSEDED.name().equals(instance.getStatus())) {
                    return;
                }
                WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
                List<WorkflowNodeDefinitionEntity> nodeDefs =
                        workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());
                runNode(instance, node, nodeDefs, workItem, supplementalInput);
            } catch (Exception e) {
                log.error("Workflow node execution failed: instance={}, node={}", instanceId, nodeInstanceId, e);
                markNodeFailed(nodeInstanceId, e);
            }
        });
    }

    private void scheduleResume(String instanceId) {
        PreparedWorkflowNode prepared;
        try {
            prepared = prepareNextPendingNode(instanceId);
        } catch (Exception e) {
            log.error("Workflow auto-resume failed before execution scheduling: instance={}", instanceId, e);
            return;
        }
        if (prepared == null) {
            return;
        }
        workflowExecutor.submit(() -> executePreparedNode(prepared));
    }

    private PreparedWorkflowNode prepareNextPendingNode(String instanceId) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            return null;
        }
        if (WorkflowStatus.SUPERSEDED.name().equals(instance.getStatus())) {
            return null;
        }

        List<WorkflowNodeInstanceEntity> nodes = getOrderedNodeInstances(instance.getId());
        boolean isBlocked = nodes.stream()
                .anyMatch(n -> WorkflowNodeStatus.WAITING_CONFIRMATION.name().equals(n.getStatus())
                        || WorkflowNodeStatus.RUNNING.name().equals(n.getStatus())
                        || WorkflowNodeStatus.FAILED.name().equals(n.getStatus()));
        if (isBlocked) {
            return null;
        }

        WorkflowNodeInstanceEntity nextPending = nodes.stream()
                .filter(n -> WorkflowNodeStatus.PENDING.name().equals(n.getStatus()))
                .findFirst().orElse(null);
        if (nextPending == null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            completeWorkflow(instance, lastCompletedNode(nodes), now);
            return null;
        }

        WorkflowNodeInstanceEntity lastCompleted = nodes.stream()
                .filter(n -> WorkflowNodeStatus.COMPLETED.name().equals(n.getStatus()))
                .reduce((first, second) -> second).orElse(null);
        if (lastCompleted != null && lastCompleted.getOutputArtifactId() != null) {
            nextPending.setInputArtifactId(lastCompleted.getOutputArtifactId());
        }

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        if (workItem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Work item not found: " + instance.getWorkItemId());
        }
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        prepareWorkflowSession(instance, nextPending, nodeDefs, workItem, now);
        WorkflowInstanceEntity preparedInstance = workflowMapper.findInstanceById(instance.getId());
        WorkflowNodeInstanceEntity preparedNode = workflowMapper.findNodeInstanceById(nextPending.getId());
        return new PreparedWorkflowNode(
                preparedInstance != null ? preparedInstance : instance,
                preparedNode != null ? preparedNode : nextPending,
                nodeDefs,
                workItem
        );
    }

    private void executePreparedNode(PreparedWorkflowNode prepared) {
        try {
            if (isWorkflowSuperseded(prepared.instance().getId())) {
                return;
            }
            executeSkillOnNode(prepared.instance(), prepared.node(), prepared.nodeDefs(), prepared.workItem());
            if (WorkflowNodeStatus.COMPLETED.name().equals(prepared.node().getStatus())) {
                scheduleResume(prepared.instance().getId());
            }
        } catch (Exception e) {
            log.error("Workflow node execution failed: instance={}, node={}",
                    prepared.instance().getId(), prepared.node().getId(), e);
            markNodeFailed(prepared.node().getId(), e);
        }
    }

    private record PreparedWorkflowNode(
            WorkflowInstanceEntity instance,
            WorkflowNodeInstanceEntity node,
            List<WorkflowNodeDefinitionEntity> nodeDefs,
            WorkItemEntity workItem
    ) {
    }

    private void markNodeFailed(String nodeInstanceId, Exception error) {
        WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            return;
        }
        String errorMessage = error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
        node.setStatus(WorkflowNodeStatus.FAILED.name());
        node.setErrorMessage(errorMessage);
        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
        if (instance != null) {
            WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
            WorkflowNodeDefinitionEntity nodeDef = workflowMapper
                    .findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId())
                    .stream()
                    .filter(definition -> definition.getId().equals(node.getNodeDefinitionId()))
                    .findFirst()
                    .orElse(null);
            if (workItem != null && nodeDef != null) {
                String skillName = node.getSkillName() != null ? node.getSkillName() : nodeDef.getSkillName();
                createFailureConfirmation(instance, node, nodeDef, workItem, skillName, errorMessage, now);
            }
            touchWorkItem(instance.getWorkItemId(), now);
        }
    }

    private List<WorkflowNodeInstanceEntity> getOrderedNodeInstances(String instanceId) {
        List<WorkflowNodeInstanceEntity> nodes =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());
        Map<String, Integer> orderMap = nodeDefs.stream()
                .collect(Collectors.toMap(WorkflowNodeDefinitionEntity::getId, WorkflowNodeDefinitionEntity::getOrderNo));
        nodes.sort(Comparator.comparingInt(n -> orderMap.getOrDefault(n.getNodeDefinitionId(), Integer.MAX_VALUE)));
        return nodes;
    }

    private void advanceToNextNode(WorkflowInstanceEntity instance) {
        if (isWorkflowPaused(instance.getId())) {
            return;
        }
        List<WorkflowNodeInstanceEntity> nodes = getOrderedNodeInstances(instance.getId());

        WorkflowNodeInstanceEntity nextPending = nodes.stream()
                .filter(n -> WorkflowNodeStatus.PENDING.name().equals(n.getStatus()))
                .findFirst().orElse(null);

        if (nextPending == null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            completeWorkflow(instance, lastCompletedNode(nodes), now);
            return;
        }

        WorkflowNodeInstanceEntity lastCompleted = nodes.stream()
                .filter(n -> WorkflowNodeStatus.COMPLETED.name().equals(n.getStatus()))
                .reduce((first, second) -> second).orElse(null);
        if (lastCompleted != null && lastCompleted.getOutputArtifactId() != null) {
            nextPending.setInputArtifactId(lastCompleted.getOutputArtifactId());
        }

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        runNodeWithFailureGuard(instance, nextPending, nodeDefs, workItem, null);
    }

    private void completeWorkflow(WorkflowInstanceEntity instance,
                                  WorkflowNodeInstanceEntity terminalNode,
                                  String now) {
        boolean alreadyCompleted = WorkflowStatus.COMPLETED.name().equals(instance.getStatus());
        instance.setStatus(WorkflowStatus.COMPLETED.name());
        instance.setCompletedAt(now);
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);
        touchWorkItem(instance.getWorkItemId(), now);
        if (!alreadyCompleted) {
            publishWorkflowCompletedStatus(instance, terminalNode);
        }
    }

    private WorkflowNodeInstanceEntity lastCompletedNode(List<WorkflowNodeInstanceEntity> nodes) {
        return nodes.stream()
                .filter(n -> WorkflowNodeStatus.COMPLETED.name().equals(n.getStatus()))
                .reduce((first, second) -> second)
                .orElse(nodes.isEmpty() ? null : nodes.get(nodes.size() - 1));
    }

    private void publishWorkflowCompletedStatus(WorkflowInstanceEntity instance,
                                                WorkflowNodeInstanceEntity terminalNode) {
        String sessionId = terminalNode != null ? terminalNode.getAgentSessionId() : null;
        if (sessionId == null || sessionId.isBlank()) {
            AgentSessionDto workflowSession = findWorkflowSession(instance);
            sessionId = workflowSession != null ? workflowSession.id() : null;
        }
        String nodeId = terminalNode != null ? terminalNode.getId() : instance.getCurrentNodeInstanceId();
        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, sessionId, instance.getWorkItemId(),
                instance.getId(), nodeId,
                RuntimeEventType.STATUS, RuntimeEventSource.WORKFLOW,
                buildWorkflowCompletedPayload(instance.getId(), nodeId), null
        ));
    }

    private String buildWorkflowCompletedPayload(String workflowInstanceId, String workflowNodeInstanceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"workflowStatus\":\"").append(WorkflowStatus.COMPLETED.name()).append("\"");
        if (workflowInstanceId != null && !workflowInstanceId.isBlank()) {
            sb.append(",\"workflowInstanceId\":\"").append(escapeJson(workflowInstanceId)).append("\"");
        }
        if (workflowNodeInstanceId != null && !workflowNodeInstanceId.isBlank()) {
            sb.append(",\"workflowNodeInstanceId\":\"").append(escapeJson(workflowNodeInstanceId)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private WorkflowInstanceEntity findActiveInstance(String workItemId) {
        List<WorkflowInstanceEntity> instances = workflowMapper.findInstancesByWorkItemId(workItemId);
        return instances.stream()
                .filter(i -> WorkflowStatus.RUNNING.name().equals(i.getStatus())
                        || WorkflowStatus.PAUSED.name().equals(i.getStatus())
                        || WorkflowStatus.BLOCKED.name().equals(i.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private WorkflowInstanceEntity findRestartTargetInstance(WorkItemEntity workItem) {
        if (workItem.getCurrentWorkflowInstanceId() != null && !workItem.getCurrentWorkflowInstanceId().isBlank()) {
            WorkflowInstanceEntity current = workflowMapper.findInstanceById(workItem.getCurrentWorkflowInstanceId());
            if (current != null && !WorkflowStatus.SUPERSEDED.name().equals(current.getStatus())) {
                return current;
            }
        }
        return findActiveInstance(workItem.getId());
    }

    private WorkflowDefinitionEntity resolveDefinition(WorkItemEntity workItem, StartWorkflowRequest request) {
        if (request.workflowDefinitionId() != null) {
            WorkflowDefinitionEntity def = workflowMapper.findDefinitionById(request.workflowDefinitionId());
            if (def != null && "ENABLED".equals(def.getStatus())) {
                return def;
            }
        }
        String projectId = ProjectDefaults.resolveProjectId(workItem.getProjectId());
        List<WorkflowDefinitionEntity> defs =
                workflowMapper.findDefinitionsByProjectIdAndWorkItemType(projectId, workItem.getType());
        if (defs.isEmpty() && !ProjectDefaults.DEFAULT_PROJECT_ID.equals(projectId)) {
            defs = workflowMapper.findDefinitionsByProjectIdAndWorkItemType(
                    ProjectDefaults.DEFAULT_PROJECT_ID, workItem.getType());
        }
        return defs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsDefault()) && "ENABLED".equals(d.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    WorkflowDefinitionEntity projectDefault =
                            workflowMapper.findDefaultEnabledDefinitionByProjectId(projectId);
                    if (projectDefault != null) {
                        return projectDefault;
                    }
                    return workflowMapper.findDefaultEnabledDefinition();
                });
    }

    private void applyRequestedExecutionMode(WorkflowInstanceEntity instance,
                                             StartWorkflowRequest request,
                                             String now) {
        if (request == null || request.mode() == null
                || WORKFLOW_MODE_START_OR_CONTINUE.equals(request.mode())) {
            return;
        }
        instance.setExecutionMode(resolveExecutionMode(request));
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);
    }

    private String resolveExecutionMode(StartWorkflowRequest request) {
        if (request == null || request.mode() == null) {
            return WORKFLOW_MODE_MANUAL_CONFIRM;
        }
        return WORKFLOW_MODE_AUTO.equals(request.mode())
                ? WORKFLOW_MODE_AUTO
                : WORKFLOW_MODE_MANUAL_CONFIRM;
    }

    private boolean isAutoRun(WorkflowInstanceEntity instance) {
        return WORKFLOW_MODE_AUTO.equals(instance.getExecutionMode());
    }

    private void touchWorkItem(String workItemId, String now) {
        WorkItemEntity workItem = workItemMapper.findById(workItemId);
        if (workItem == null) {
            return;
        }
        workItem.setUpdatedAt(now);
        workItemMapper.update(workItem);
    }

    private String buildNodeStatusMessage(SkillRunResult result,
                                          WorkflowNodeDefinitionEntity nodeDef,
                                          String skillName,
                                          ArtifactEntity artifact,
                                          WorkflowNodeState nodeState) {
        if (result.success()) {
            String artifactText = artifact != null
                    ? "，产物：" + artifact.getTitle() + artifactReferenceMarker(artifact)
                    : "";
            return switch (nodeState.getStatus()) {
                case READY_TO_ADVANCE -> "已完成 %s（Skill：%s）%s".formatted(nodeDef.getName(), skillName, artifactText);
                case NEEDS_USER_INPUT -> "%s（Skill：%s）需要用户补充/确认；处理后会回到当前 Skill 继续执行。"
                        .formatted(nodeDef.getName(), skillName);
                case BLOCKED -> "%s（Skill：%s）被阻塞：%s".formatted(
                        nodeDef.getName(), skillName, nonBlank(nodeState.getReason(), "原因未知"));
                case IN_PROGRESS -> "%s（Skill：%s）仍在执行中。".formatted(nodeDef.getName(), skillName);
            };
        }
        String reason = result.errorMessage() != null && !result.errorMessage().isBlank()
                ? "：" + result.errorMessage()
                : "";
        return "执行失败 %s（Skill：%s）%s".formatted(nodeDef.getName(), skillName, reason);
    }

    private String buildInputContext(WorkItemEntity workItem,
                                     WorkflowNodeInstanceEntity node,
                                     WorkflowNodeDefinitionEntity nodeDef) {
        return buildInputContext(workItem, node, nodeDef, null, null);
    }

    private String buildInputContext(WorkItemEntity workItem,
                                     WorkflowNodeInstanceEntity node,
                                     WorkflowNodeDefinitionEntity nodeDef,
                                     String supplementalInput) {
        return buildInputContext(workItem, node, nodeDef, supplementalInput, null);
    }

    private String buildInputContext(WorkItemEntity workItem,
                                     WorkflowNodeInstanceEntity node,
                                     WorkflowNodeDefinitionEntity nodeDef,
                                     String supplementalInput,
                                     String contextAnchorSection) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 工作流节点执行输入\n\n");
        sb.append("## 工作项\n");
        appendField(sb, "ID", workItem.getId());
        appendField(sb, "编号", workItem.getCode());
        appendField(sb, "标题", workItem.getTitle());
        appendField(sb, "类型", workItem.getType());
        appendField(sb, "状态", workItem.getStatus());
        appendField(sb, "优先级", workItem.getPriority());
        appendField(sb, "项目", workItem.getProjectId());
        appendField(sb, "空间", workItem.getSpaceId());
        appendField(sb, "迭代", workItem.getIterationId());
        appendField(sb, "负责人", workItem.getAssigneeUserId());
        sb.append("- 描述：\n\n");
        sb.append("```text\n");
        sb.append(nonBlank(workItem.getDescription(), "暂无描述"));
        sb.append("\n```\n\n");

        sb.append("## 当前节点\n");
        appendField(sb, "节点ID", node.getId());
        appendField(sb, "节点名称", nodeDef.getName());
        appendField(sb, "节点Key", nodeDef.getNodeKey());
        appendField(sb, "阶段Key", nonBlank(node.getStageKey(), nodeDef.getStageKey()));
        appendField(sb, "Skill", nodeDef.getSkillName());
        appendField(sb, "输入策略", nodeDef.getInputPolicy());
        appendField(sb, "输出类型", ArtifactType.MARKDOWN.name());
        appendField(sb, "需要确认", Boolean.TRUE.equals(nodeDef.getRequiredConfirmation()) ? "是" : "否");
        if (nodeDef.getStageGoal() != null && !nodeDef.getStageGoal().isBlank()) {
            appendField(sb, "阶段目标", nodeDef.getStageGoal());
        }
        sb.append("\n");

        appendContextAnchor(sb, contextAnchorSection);
        appendUpstreamArtifacts(sb, node);

        appendResolvedInteractionHistory(sb, workItem.getId(), node.getId());
        appendSupplementalInput(sb, supplementalInput);

        sb.append("## 执行方式\n");
        sb.append("- 请按 Skill 自身说明处理上述输入。\n");
        sb.append("- AgentCenter 页面是用户可介入的协作界面；用户可以随时输入补充、调整、继续或接管指令。\n");
        sb.append("- 工作流只提供调用顺序、当前输入和上游输出，不替代 Skill 的判断，也不替代用户本轮输入。\n");
        sb.append("- 如果存在“用户本轮输入”，它是当前节点的自然多轮指令；优先围绕这句话继续、修正或扩展当前输出。\n");
        sb.append("- 用户说“继续”通常表示继续当前 Skill 的未完成内容；除非用户明确要求进入下一节点，不要把它解读为推进确认。\n");
        sb.append("- 不要用“等待系统推进”“可在适当时机推进”这类流程占位话术替代对用户本轮输入的实际响应；能继续就直接继续。\n");
        sb.append("- 如果还需要用户澄清、选择、确认或授权，优先使用 OpenCode 原生 Question 交互；AgentCenter Bridge 会将 Question 翻译为平台待确认。\n");
        sb.append("- 如果当前 Runtime 不能使用 Question，再在输出末尾按 AgentCenter 节点状态协议声明 NEEDS_USER_INPUT。\n");
        sb.append("- 如果信息已经足够，请输出当前 Skill 的最终 Markdown 结果。\n");
        return sb.toString();
    }

    private void appendContextAnchor(StringBuilder sb, String contextAnchorSection) {
        if (contextAnchorSection == null || contextAnchorSection.isBlank()) {
            return;
        }
        sb.append(contextAnchorSection);
    }

    private void appendUpstreamArtifacts(StringBuilder sb, WorkflowNodeInstanceEntity node) {
        List<ArtifactEntity> upstreamArtifacts = findUpstreamArtifacts(node);
        sb.append("## 上游产物\n");
        if (upstreamArtifacts.isEmpty()) {
            sb.append("无。该节点应基于工作项本身生成结果。\n\n");
            return;
        }

        if (upstreamArtifacts.size() > 1) {
            sb.append("以下为当前节点之前所有已完成节点的产物，请综合使用；不要只依赖对话历史。\n\n");
        }

        for (int i = 0; i < upstreamArtifacts.size(); i++) {
            ArtifactEntity input = upstreamArtifacts.get(i);
            if (upstreamArtifacts.size() > 1) {
                sb.append("### 上游产物 ").append(i + 1)
                        .append("：").append(nonBlank(input.getTitle(), input.getId()))
                        .append("\n");
            }
            appendField(sb, "artifactId", input.getId());
            appendField(sb, "title", input.getTitle());
            appendField(sb, "type", input.getArtifactType());
            appendField(sb, "sourceNodeInstanceId", input.getWorkflowNodeInstanceId());
            sb.append("\n### 上游产物内容\n\n");
            sb.append("```markdown\n");
            sb.append(artifactContentForPrompt(input));
            sb.append("\n```\n\n");
        }
    }

    private List<ArtifactEntity> findUpstreamArtifacts(WorkflowNodeInstanceEntity node) {
        java.util.Set<String> artifactIds = new java.util.LinkedHashSet<>();
        Integer currentSequence = node.getSequenceNo();

        if (node.getWorkflowInstanceId() != null && currentSequence != null) {
            workflowMapper.findNodeInstancesByWorkflowInstanceId(node.getWorkflowInstanceId()).stream()
                    .filter(candidate -> !node.getId().equals(candidate.getId()))
                    .filter(candidate -> candidate.getSequenceNo() != null)
                    .filter(candidate -> candidate.getSequenceNo() < currentSequence)
                    .sorted(Comparator.comparingInt(WorkflowNodeInstanceEntity::getSequenceNo))
                    .map(WorkflowNodeInstanceEntity::getOutputArtifactId)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(artifactIds::add);
        }

        if (node.getInputArtifactId() != null && !node.getInputArtifactId().isBlank()) {
            artifactIds.add(node.getInputArtifactId());
        }

        return artifactIds.stream()
                .map(artifactMapper::findById)
                .filter(artifact -> artifact != null && artifactContentForPrompt(artifact) != null)
                .toList();
    }

    private void appendResolvedInteractionHistory(StringBuilder sb, String workItemId, String nodeInstanceId) {
        List<ConfirmationRequestEntity> resolvedInteractions = confirmationMapper.findByWorkItemId(workItemId).stream()
                .filter(confirmation -> nodeInstanceId.equals(confirmation.getWorkflowNodeInstanceId()))
                .filter(confirmation -> ConfirmationStatus.RESOLVED.name().equals(confirmation.getStatus()))
                .toList();
        if (resolvedInteractions.isEmpty()) {
            return;
        }

        sb.append("## 用户交互回答历史\n");
        for (ConfirmationRequestEntity confirmation : resolvedInteractions) {
            appendField(sb, "确认项", nonBlank(confirmation.getTitle(), confirmation.getId()));
            appendField(sb, "原始问题", confirmation.getContent());
            appendField(sb, "交互类型", confirmation.getRequestType());
            appendField(sb, "用户处理", confirmation.getResolutionPayloadJson());
            appendField(sb, "用户备注", confirmation.getResolutionComment());
            sb.append("\n");
        }
    }

    private void appendSupplementalInput(StringBuilder sb, String supplementalInput) {
        if (supplementalInput == null || supplementalInput.isBlank()) {
            return;
        }
        sb.append("## 用户本轮输入（优先执行）\n\n");
        sb.append("以下内容来自用户在页面中的主动介入。它是当前节点的直接指令；请优先按它继续、修正或扩展当前输出，不要只回复等待平台推进。\n\n");
        sb.append("```text\n");
        sb.append(supplementalInput.trim());
        sb.append("\n```\n\n");
    }

    private void appendField(StringBuilder sb, String label, String value) {
        sb.append("- ").append(label).append("：").append(nonBlank(value, "未提供")).append("\n");
    }

    private String artifactReferenceMarker(ArtifactEntity artifact) {
        if (artifact == null || artifact.getId() == null || artifact.getId().isBlank()) {
            return "";
        }
        return "\n<!-- AGENTCENTER_ARTIFACT artifactId: " + artifact.getId().trim() + " -->";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stageKeyFor(WorkflowNodeDefinitionEntity nodeDef) {
        if (nodeDef.getStageKey() != null && !nodeDef.getStageKey().isBlank()) {
            return nodeDef.getStageKey();
        }
        return nodeDef.getNodeKey();
    }

    private StartWorkflowResponse buildResponse(WorkflowInstanceEntity instance) {
        WorkflowInstanceDto instanceDto = toInstanceDto(instance);

        List<WorkflowNodeInstanceEntity> nodeEntities =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId());

        AgentSessionDto session = null;
        if (instance.getCurrentNodeInstanceId() != null) {
            session = nodeEntities.stream()
                    .filter(n -> n.getId().equals(instance.getCurrentNodeInstanceId()))
                    .filter(n -> n.getAgentSessionId() != null)
                    .map(n -> {
                        try {
                            return agentSessionService.getSession(n.getAgentSessionId());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .findFirst().orElse(null);
        }

        List<ArtifactDto> artifacts = nodeEntities.stream()
                .filter(n -> n.getOutputArtifactId() != null)
                .map(n -> {
                    ArtifactEntity e = artifactMapper.findById(n.getOutputArtifactId());
                    return e != null ? toArtifactDto(e) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        List<RuntimeEventDto> events = nodeEntities.stream()
                .filter(n -> n.getAgentSessionId() != null)
                .flatMap(n -> runtimeEventService.getEventsBySession(n.getAgentSessionId()).stream())
                .toList();

        ConfirmationRequestDto confirmation = nodeEntities.stream()
                .filter(n -> WorkflowNodeStatus.WAITING_CONFIRMATION.name().equals(n.getStatus()))
                .findFirst()
                .map(n -> {
                    List<com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity> confs =
                            confirmationMapper.findByWorkItemId(instance.getWorkItemId());
                    return confs.stream()
                            .filter(c -> instance.getId().equals(c.getWorkflowInstanceId())
                                    && n.getId().equals(c.getWorkflowNodeInstanceId())
                                    && ConfirmationStatus.PENDING.name().equals(c.getStatus()))
                            .findFirst()
                            .map(this::toConfirmationDto)
                            .orElse(null);
                }).orElse(null);

        return new StartWorkflowResponse(instanceDto, session, artifacts, events, confirmation);
    }

    private WorkflowNodeInstanceEntity findNodeInstanceOrThrow(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Workflow node instance not found: " + nodeInstanceId);
        }
        return node;
    }

    private void ensureWorkflowMutable(WorkflowInstanceEntity instance) {
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found");
        }
        if (WorkflowStatus.SUPERSEDED.name().equals(instance.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow instance has been superseded by a newer version.");
        }
    }

    private boolean isWorkflowSuperseded(String workflowInstanceId) {
        WorkflowInstanceEntity latest = workflowMapper.findInstanceById(workflowInstanceId);
        return latest != null && WorkflowStatus.SUPERSEDED.name().equals(latest.getStatus());
    }

    private boolean isWorkflowPaused(String workflowInstanceId) {
        WorkflowInstanceEntity latest = workflowMapper.findInstanceById(workflowInstanceId);
        return latest != null && WorkflowStatus.PAUSED.name().equals(latest.getStatus());
    }

    private WorkflowInstanceDto toInstanceDto(WorkflowInstanceEntity e) {
        List<WorkflowNodeInstanceEntity> nodeEntities =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(e.getId());

        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(e.getWorkflowDefinitionId());
        java.util.Map<String, Integer> nodeOrderMap = nodeDefs.stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowNodeDefinitionEntity::getId,
                        WorkflowNodeDefinitionEntity::getOrderNo));

        nodeEntities.sort(Comparator.comparingInt(n ->
                nodeOrderMap.getOrDefault(n.getNodeDefinitionId(), Integer.MAX_VALUE)));

        List<WorkflowNodeInstanceDto> nodes = nodeEntities.stream()
                .map(this::toNodeInstanceDto)
                .toList();
        return new WorkflowInstanceDto(
                e.getId(),
                e.getWorkItemId(),
                e.getWorkflowDefinitionId(),
                WorkflowStatus.valueOf(e.getStatus()),
                e.getCurrentNodeInstanceId(),
                nodes,
                parseDateTime(e.getStartedAt()),
                parseDateTime(e.getCompletedAt())
        );
    }

    private WorkflowNodeInstanceDto toNodeInstanceDto(WorkflowNodeInstanceEntity e) {
        return new WorkflowNodeInstanceDto(
                e.getId(),
                e.getNodeDefinitionId(),
                WorkflowNodeStatus.valueOf(e.getStatus()),
                e.getInputArtifactId(),
                e.getOutputArtifactId(),
                e.getAgentSessionId(),
                parseDateTime(e.getStartedAt()),
                parseDateTime(e.getCompletedAt()),
                e.getErrorMessage(),
                e.getNodeKind(),
                e.getOrigin(),
                e.getParentNodeInstanceId(),
                e.getStageKey(),
                e.getSkillName(),
                e.getSummary(),
                e.getReason(),
                e.getSequenceNo(),
                e.getAgentState(),
                e.getAgentStateReason()
        );
    }

    private ArtifactDto toArtifactDto(ArtifactEntity e) {
        return new ArtifactDto(
                e.getId(),
                e.getWorkItemId(),
                e.getWorkflowInstanceId(),
                e.getWorkflowNodeInstanceId(),
                ArtifactType.valueOf(e.getArtifactType()),
                e.getTitle(),
                e.getContent(),
                e.getSourceType(),
                firstNonBlank(e.getFilePath(), e.getStorageUri()),
                parseDateTime(e.getCreatedAt())
        );
    }

    private ConfirmationRequestDto toConfirmationDto(ConfirmationRequestEntity e) {
        WorkItemEntity workItem = e.getWorkItemId() != null ? workItemMapper.findById(e.getWorkItemId()) : null;
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
                Priority.valueOf(e.getPriority()),
                parseDateTime(e.getCreatedAt()),
                e.getInteractionId(),
                e.getInteractionType(),
                e.getInteractionSchemaJson(),
                e.getInteractionContextJson(),
                e.getInteractionRequired() != null ? e.getInteractionRequired() != 0 : null,
                e.getInteractionOrderNo()
        );
    }

    private String resolveWorkflowNodeName(String nodeInstanceId) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return null;
        }
        WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            return null;
        }
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());
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

    private record FileArtifactSnapshot(Path path, long modifiedMillis, long size) {}
}
