package com.agentcenter.bridge.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.api.dto.StartWorkflowRequest;
import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.api.dto.WorkflowInstanceDto;
import com.agentcenter.bridge.api.dto.WorkflowNodeInstanceDto;
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
import com.agentcenter.bridge.application.workflow.WorkflowPromptComposer;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.domain.workflow.WorkflowNodeStatus;
import com.agentcenter.bridge.domain.workflow.WorkflowStatus;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteraction;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeState;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateParser;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeStateStatus;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ArtifactMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillMapper;
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

    private final WorkflowMapper workflowMapper;
    private final WorkItemMapper workItemMapper;
    private final ArtifactMapper artifactMapper;
    private final ConfirmationMapper confirmationMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentSessionService agentSessionService;
    private final RuntimeEventService runtimeEventService;
    private final RuntimeGateway runtimeGateway;
    private final RuntimeSkillMapper runtimeSkillMapper;
    private final IdGenerator idGenerator;
    private final RuntimeType workflowRuntimeType;
    private final ExecutorService workflowExecutor;
    private final InteractionMapper interactionMapper = new InteractionMapper();
    private final WorkflowPromptComposer workflowPromptComposer = new WorkflowPromptComposer();

    public WorkflowCommandService(WorkflowMapper workflowMapper,
                                   WorkItemMapper workItemMapper,
                                   ArtifactMapper artifactMapper,
                                   ConfirmationMapper confirmationMapper,
                                   AgentMessageMapper agentMessageMapper,
                                   AgentSessionService agentSessionService,
                                   RuntimeEventService runtimeEventService,
                                   RuntimeGateway runtimeGateway,
                                   RuntimeSkillMapper runtimeSkillMapper,
                                   IdGenerator idGenerator,
                                   @Value("${agentcenter.runtime.default-type:OPENCODE}") String defaultRuntimeType,
                                   @Qualifier("workflowExecutor") ExecutorService workflowExecutor) {
        this.workflowMapper = workflowMapper;
        this.workItemMapper = workItemMapper;
        this.artifactMapper = artifactMapper;
        this.confirmationMapper = confirmationMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentSessionService = agentSessionService;
        this.runtimeEventService = runtimeEventService;
        this.runtimeGateway = runtimeGateway;
        this.runtimeSkillMapper = runtimeSkillMapper;
        this.idGenerator = idGenerator;
        this.workflowRuntimeType = RuntimeType.valueOf(defaultRuntimeType.toUpperCase());
        this.workflowExecutor = workflowExecutor;
    }

    @PreDestroy
    public void shutdown() {
        workflowExecutor.shutdownNow();
    }

    public StartWorkflowResponse startWorkflow(String workItemId, StartWorkflowRequest request) {
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

    public StartWorkflowResponse continueWorkflow(String instanceId) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found: " + instanceId);
        }

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
            instance.setStatus(WorkflowStatus.COMPLETED.name());
            instance.setCompletedAt(now);
            instance.setUpdatedAt(now);
            workflowMapper.updateInstance(instance);
            return buildResponse(instance);
        }

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        runNode(instance, nextPending, nodeDefs, workItem);

        instance = workflowMapper.findInstanceById(instanceId);
        return buildResponse(instance);
    }

    public StartWorkflowResponse retryNode(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());

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
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        node.setStatus(WorkflowNodeStatus.RUNNING.name());
        node.setErrorMessage(null);
        workflowMapper.updateNodeInstance(node);
        touchWorkItem(instance.getWorkItemId(), now);

        scheduleRunNode(instance.getId(), node.getId());

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
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        prepareWorkflowSession(instance, node, nodeDefs, workItem, now);

        executeSkillOnNode(instance, node, nodeDefs, workItem);

        if (WorkflowNodeStatus.COMPLETED.name().equals(node.getStatus())) {
            advanceToNextNode(instance);
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
        WorkflowNodeDefinitionEntity nodeDef = nodeDefs.stream()
                .filter(d -> d.getId().equals(node.getNodeDefinitionId()))
                .findFirst().orElseThrow();

        String inputContext = buildInputContext(workItem, node, nodeDef);
        String skillName = nodeDef.getSkillName();
        String toolCallId = workflowToolCallId(node, skillName);
        insertWorkflowContextMessageIfAbsent(node.getAgentSessionId(), node, nodeDef, workItem, inputContext);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_STARTED, RuntimeEventSource.WORKFLOW,
                buildSkillStartedPayload(skillName, toolCallId), null
        ));

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
                SkillInvocationRequest request = workflowPromptComposer.composeInvocationRequest(skillName, inputContext);
                result = runtimeGateway.runSkill(
                        workflowRuntimeType, node.getRuntimeSessionId(), request);
            }
        }

        WorkflowNodeState nodeState = result.success()
                ? WorkflowNodeStateParser.parse(result.outputContent())
                : WorkflowNodeState.defaultInProgress();

        boolean shouldPersistFinalArtifact = result.success()
                && nodeState.getStatus() == WorkflowNodeStateStatus.READY_TO_ADVANCE
                && result.outputContent() != null;

        ArtifactEntity artifact = null;
        if (shouldPersistFinalArtifact) {
            String artifactTitle = nodeState.getArtifactTitle() != null && !nodeState.getArtifactTitle().isBlank()
                    ? nodeState.getArtifactTitle()
                    : "%s-%s.md".formatted(workItem.getCode(), nodeDef.getName());

            artifact = new ArtifactEntity();
            artifact.setId(idGenerator.nextId());
            artifact.setWorkItemId(workItem.getId());
            artifact.setWorkflowInstanceId(instance.getId());
            artifact.setWorkflowNodeInstanceId(node.getId());
            artifact.setSessionId(node.getAgentSessionId());
            artifact.setArtifactType(ArtifactType.MARKDOWN.name());
            artifact.setTitle(artifactTitle);
            artifact.setContent(WorkflowNodeStateParser.stripStateBlock(result.outputContent()));
            artifact.setVersionNo(1);
            artifact.setCreatedBy(workflowRuntimeType.name());
            artifact.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            artifactMapper.insert(artifact);

            node.setOutputArtifactId(artifact.getId());

            insertNodeOutputMessageIfAbsent(
                    node.getAgentSessionId(), nodeDef.getName(), skillName,
                    WorkflowNodeStateParser.stripStateBlock(result.outputContent()), artifact.getTitle(), node.getId());
        }

        String sessionId = node.getAgentSessionId();
        int nextSeqNo = nextMessageSeqNo(sessionId);
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        AgentMessageEntity statusMsg = new AgentMessageEntity();
        statusMsg.setId(idGenerator.nextId());
        statusMsg.setSessionId(sessionId);
        statusMsg.setRole(MessageRole.SYSTEM.name());
        statusMsg.setContent(buildNodeStatusMessage(
                result, nodeDef, skillName, artifact, nodeState));
        statusMsg.setContentFormat(ContentFormat.TEXT.name());
        statusMsg.setStatus(MessageStatus.COMPLETED.name());
        statusMsg.setSeqNo(nextSeqNo);
        statusMsg.setCreatedBy("workflow-engine");
        statusMsg.setCreatedAt(now);
        agentMessageMapper.insert(statusMsg);

        List<ConfirmationRequestEntity> pendingConfirmations = new ArrayList<>();

        if (!result.success()) {
            node.setStatus(WorkflowNodeStatus.FAILED.name());
            node.setErrorMessage(result.errorMessage());
            node.setCompletedAt(now);
            workflowMapper.updateNodeInstance(node);
            createFailureConfirmation(instance, node, nodeDef, workItem, skillName, result.errorMessage(), now);
        } else {
            switch (nodeState.getStatus()) {
                case READY_TO_ADVANCE -> {
                    node.setAgentState(nodeState.getStatus().name());
                    node.setAgentStateReason(nodeState.getReason());
                    node.setAgentStateUpdatedAt(now);
                    if (nodeState.getArtifactTitle() != null) {
                        node.setAgentStateArtifactTitle(nodeState.getArtifactTitle());
                    }

                    if (isAutoRun(instance)) {
                        node.setStatus(WorkflowNodeStatus.COMPLETED.name());
                        node.setCompletedAt(now);
                        instance.setStatus(WorkflowStatus.RUNNING.name());
                        instance.setUpdatedAt(now);
                        workflowMapper.updateInstance(instance);
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
                    if (nodeState.getInteractions() != null && !nodeState.getInteractions().isEmpty()) {
                        int orderNo = 0;
                        for (WorkflowNodeInteraction interaction : nodeState.getInteractions()) {
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
                                nodeState.getReason(), now);
                        confirmationMapper.insert(inputRequest);
                        pendingConfirmations.add(inputRequest);
                    }
                    node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());

                    node.setAgentState(nodeState.getStatus().name());
                    node.setAgentStateReason(nodeState.getReason());
                    node.setAgentStateUpdatedAt(now);

                    markInstanceBlocked(instance, now);
                }
                case BLOCKED -> {
                    createFailureConfirmation(instance, node, nodeDef, workItem, skillName,
                            nodeState.getReason(), now);
                    node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());

                    node.setAgentState(nodeState.getStatus().name());
                    node.setAgentStateReason(nodeState.getReason());
                    node.setAgentStateUpdatedAt(now);

                    markInstanceBlocked(instance, now);
                }
                case IN_PROGRESS -> {
                    // Stays RUNNING — no artifact, no advance
                    node.setAgentState(nodeState.getStatus().name());
                    node.setAgentStateReason(nodeState.getReason());
                    node.setAgentStateUpdatedAt(now);
                }
            }
        }

        if (!node.getStatus().equals(WorkflowNodeStatus.FAILED.name())) {
            workflowMapper.updateNodeInstance(node);
        }
        touchWorkItem(workItem.getId(), now);

        String statePayload = nodeState.getStatus().name();
        String stateReason = nodeState.getReason() != null ? nodeState.getReason() : "";
        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_COMPLETED, RuntimeEventSource.WORKFLOW,
                buildSkillCompletedPayload(skillName, toolCallId, result, statePayload, stateReason),
                null
        ));

        for (ConfirmationRequestEntity confirmation : pendingConfirmations) {
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null, node.getAgentSessionId(), workItem.getId(),
                    instance.getId(), node.getId(),
                    RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                    "{\"confirmationId\":\"" + confirmation.getId() + "\"}", null
            ));
        }
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
        confirmation.setOptionsJson("[\"重试当前节点\",\"跳过该节点继续\"]");
        confirmation.setPriority(Priority.HIGH.name());
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.insert(confirmation);

        markInstanceBlocked(instance, now);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                "{\"confirmationId\":\"" + confirmation.getId() + "\"}", null
        ));
    }

    private String buildFailureConfirmationContent(String detail) {
        return """
                节点执行失败，需要用户决定后续处理方式。

                失败原因：%s

                可选择重试当前节点；如果确认该节点暂时不阻塞后续流程，也可以跳过。
                """.formatted(detail).trim();
    }

    private void markInstanceBlocked(WorkflowInstanceEntity instance, String now) {
        instance.setStatus(WorkflowStatus.BLOCKED.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);
    }

    private void insertNodeOutputMessageIfAbsent(String sessionId, String nodeName,
                                                  String skillName, String markdownContent,
                                                  String artifactTitle, String nodeInstanceId) {
        if (markdownContent == null || markdownContent.isBlank() || sessionId == null) {
            return;
        }
        if (isDuplicateLatestAssistant(sessionId, markdownContent)) {
            return;
        }
        AgentMessageEntity msg = new AgentMessageEntity();
        msg.setId(idGenerator.nextId());
        msg.setSessionId(sessionId);
        msg.setRole(MessageRole.ASSISTANT.name());
        msg.setContent(markdownContent);
        msg.setContentFormat(ContentFormat.MARKDOWN.name());
        msg.setStatus(MessageStatus.COMPLETED.name());
        msg.setSeqNo(nextMessageSeqNo(sessionId));
        msg.setCreatedBy("workflow-engine");
        msg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        msg.setWorkflowNodeInstanceId(nodeInstanceId);
        agentMessageMapper.insert(msg);
    }

    private boolean isDuplicateLatestAssistant(String sessionId, String content) {
        return agentMessageMapper.findBySessionId(sessionId).stream()
                .filter(m -> MessageRole.ASSISTANT.name().equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(m -> content.equals(m.getContent()))
                .orElse(false);
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
        confirmation.setRequestType(ConfirmationRequestType.INPUT_REQUIRED.name());
        confirmation.setStatus(ConfirmationStatus.PENDING.name());
        confirmation.setWorkItemId(workItem.getId());
        confirmation.setWorkflowInstanceId(instance.getId());
        confirmation.setWorkflowNodeInstanceId(node.getId());
        confirmation.setAgentSessionId(node.getAgentSessionId());
        confirmation.setRuntimeType(workflowRuntimeType.name());
        confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
        confirmation.setSkillName(skillName);
        confirmation.setTitle("%s %s · 需要补充输入".formatted(workItem.getCode(), nodeDef.getName()));
        confirmation.setContent(nonBlank(reason, "Agent 需要用户补充信息"));
        confirmation.setContextSummary(reason);
        confirmation.setInteractionType("INPUT");
        confirmation.setInteractionRequired(1);
        confirmation.setPriority("MEDIUM");
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        return confirmation;
    }

    private SkillRunResult validateSkillRunnable(WorkItemEntity workItem, String skillName) {
        RuntimeSkillEntity skill = runtimeSkillMapper.findByProjectIdAndName(workItem.getProjectId(), skillName);
        if (skill == null) {
            if (runtimeSkillMapper.countByProjectId(workItem.getProjectId()) == 0) {
                log.debug("Runtime skill registry is empty for project {}; allowing legacy skill execution: {}",
                        workItem.getProjectId(), skillName);
                return null;
            }
            return new SkillRunResult(false, null, null,
                    "Skill is not registered for project " + workItem.getProjectId() + ": " + skillName, false);
        }
        if (!"ENABLED".equals(skill.getStatus()) || !"VALID".equals(skill.getValidationStatus())) {
            String status = skill.getStatus() == null ? "UNKNOWN" : skill.getStatus();
            String validation = skill.getValidationStatus() == null ? "UNKNOWN" : skill.getValidationStatus();
            return new SkillRunResult(false, null, null,
                    "Skill is not runnable: " + skillName + " status=" + status + ", validation=" + validation, false);
        }
        return null;
    }

    private String workflowToolCallId(WorkflowNodeInstanceEntity node, String skillName) {
        return "workflow:" + node.getId() + ":" + skillName;
    }

    private String buildSkillStartedPayload(String skillName, String toolCallId) {
        return "{\"skillName\":\"" + escapeJson(skillName) + "\",\"toolCallId\":\"" + escapeJson(toolCallId) + "\"}";
    }

    private String buildSkillCompletedPayload(String skillName, String toolCallId, SkillRunResult result, String nodeState, String nodeStateReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"skillName\":\"").append(escapeJson(skillName)).append("\"");
        sb.append(",\"toolCallId\":\"").append(escapeJson(toolCallId)).append("\"");
        sb.append(",\"success\":").append(result.success());
        if (!result.success() && result.errorMessage() != null && !result.errorMessage().isBlank()) {
            sb.append(",\"errorMessage\":\"").append(escapeJson(result.errorMessage())).append("\"");
        }
        sb.append(",\"nodeState\":\"").append(escapeJson(nodeState)).append("\"");
        if (nodeStateReason != null && !nodeStateReason.isBlank()) {
            sb.append(",\"nodeStateReason\":\"").append(escapeJson(nodeStateReason)).append("\"");
        }
        sb.append("}");
        return sb.toString();
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

    private synchronized void insertWorkflowContextMessageIfAbsent(String sessionId,
                                                                   WorkflowNodeInstanceEntity node,
                                                                   WorkflowNodeDefinitionEntity nodeDef,
                                                                   WorkItemEntity workItem,
                                                                   String inputContext) {
        if (agentMessageMapper.findBySessionId(sessionId).stream()
                .anyMatch(message -> MessageRole.USER.name().equals(message.getRole())
                        && node.getId().equals(message.getWorkflowNodeInstanceId()))) {
            return;
        }
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
        contextMsg.setSeqNo(nextMessageSeqNo(sessionId));
        contextMsg.setCreatedBy("workflow-engine");
        contextMsg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        contextMsg.setWorkflowNodeInstanceId(node.getId());
        agentMessageMapper.insert(contextMsg);
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
        contextMsg.setSeqNo(nextMessageSeqNo(sessionId));
        contextMsg.setCreatedBy("workflow-engine");
        contextMsg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        agentMessageMapper.insert(contextMsg);
    }

    private int nextMessageSeqNo(String sessionId) {
        return agentMessageMapper.findBySessionId(sessionId).stream()
                .mapToInt(m -> m.getSeqNo() != null ? m.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;
    }

    private void scheduleRunNode(String instanceId, String nodeInstanceId) {
        workflowExecutor.submit(() -> {
            try {
                WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
                WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
                if (instance == null || node == null) {
                    return;
                }
                WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
                List<WorkflowNodeDefinitionEntity> nodeDefs =
                        workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());
                runNode(instance, node, nodeDefs, workItem);
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
            instance.setStatus(WorkflowStatus.COMPLETED.name());
            instance.setCompletedAt(now);
            instance.setUpdatedAt(now);
            workflowMapper.updateInstance(instance);
            touchWorkItem(instance.getWorkItemId(), now);
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
        List<WorkflowNodeInstanceEntity> nodes = getOrderedNodeInstances(instance.getId());

        WorkflowNodeInstanceEntity nextPending = nodes.stream()
                .filter(n -> WorkflowNodeStatus.PENDING.name().equals(n.getStatus()))
                .findFirst().orElse(null);

        if (nextPending == null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            instance.setStatus(WorkflowStatus.COMPLETED.name());
            instance.setCompletedAt(now);
            instance.setUpdatedAt(now);
            workflowMapper.updateInstance(instance);
            touchWorkItem(instance.getWorkItemId(), now);
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

        runNode(instance, nextPending, nodeDefs, workItem);
    }

    private WorkflowInstanceEntity findActiveInstance(String workItemId) {
        List<WorkflowInstanceEntity> instances = workflowMapper.findInstancesByWorkItemId(workItemId);
        return instances.stream()
                .filter(i -> WorkflowStatus.RUNNING.name().equals(i.getStatus())
                        || WorkflowStatus.BLOCKED.name().equals(i.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private WorkflowDefinitionEntity resolveDefinition(WorkItemEntity workItem, StartWorkflowRequest request) {
        if (request.workflowDefinitionId() != null) {
            WorkflowDefinitionEntity def = workflowMapper.findDefinitionById(request.workflowDefinitionId());
            if (def != null && "ENABLED".equals(def.getStatus())) {
                return def;
            }
        }
        List<WorkflowDefinitionEntity> defs =
                workflowMapper.findDefinitionsByWorkItemType(workItem.getType());
        return defs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsDefault()) && "ENABLED".equals(d.getStatus()))
                .findFirst()
                .orElseGet(workflowMapper::findDefaultEnabledDefinition);
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
            String artifactText = artifact != null ? "，产物：" + artifact.getTitle() : "";
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

        if (node.getInputArtifactId() != null) {
            ArtifactEntity input = artifactMapper.findById(node.getInputArtifactId());
            if (input != null && input.getContent() != null) {
                sb.append("## 上游产物\n");
                appendField(sb, "artifactId", input.getId());
                appendField(sb, "title", input.getTitle());
                appendField(sb, "type", input.getArtifactType());
                appendField(sb, "sourceNodeInstanceId", input.getWorkflowNodeInstanceId());
                sb.append("\n### 上游产物内容\n\n");
                sb.append("```markdown\n");
                sb.append(input.getContent());
                sb.append("\n```\n\n");
            }
        } else {
            sb.append("## 上游产物\n");
            sb.append("无。该节点应基于工作项本身生成结果。\n\n");
        }

        appendResolvedInteractionHistory(sb, workItem.getId(), node.getId());

        sb.append("## 执行方式\n");
        sb.append("- 请按 Skill 自身说明处理上述输入。\n");
        sb.append("- 工作流只提供调用顺序、当前输入和上游输出，不替代 Skill 的判断。\n");
        sb.append("- 如果还需要用户澄清，请直接提出问题或给出可选方案。\n");
        sb.append("- 如果信息已经足够，请输出当前 Skill 的最终 Markdown 结果。\n");
        return sb.toString();
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

    private void appendField(StringBuilder sb, String label, String value) {
        sb.append("- ").append(label).append("：").append(nonBlank(value, "未提供")).append("\n");
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
}
