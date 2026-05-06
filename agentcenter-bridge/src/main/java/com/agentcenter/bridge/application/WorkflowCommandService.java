package com.agentcenter.bridge.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.agentcenter.bridge.application.runtime.AgentRuntimeAdapter;
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
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workflow.WorkflowNodeStatus;
import com.agentcenter.bridge.domain.workflow.WorkflowStatus;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;
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
    private static final AtomicInteger WORKFLOW_THREAD_COUNTER = new AtomicInteger(1);

    private final WorkflowMapper workflowMapper;
    private final WorkItemMapper workItemMapper;
    private final ArtifactMapper artifactMapper;
    private final ConfirmationMapper confirmationMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentSessionService agentSessionService;
    private final RuntimeEventService runtimeEventService;
    private final AgentRuntimeAdapter runtimeAdapter;
    private final IdGenerator idGenerator;
    private final RuntimeType workflowRuntimeType;
    private final ExecutorService workflowExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "agentcenter-workflow-" + WORKFLOW_THREAD_COUNTER.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public WorkflowCommandService(WorkflowMapper workflowMapper,
                                   WorkItemMapper workItemMapper,
                                   ArtifactMapper artifactMapper,
                                   ConfirmationMapper confirmationMapper,
                                   AgentMessageMapper agentMessageMapper,
                                   AgentSessionService agentSessionService,
                                   RuntimeEventService runtimeEventService,
                                   AgentRuntimeAdapter runtimeAdapter,
                                   IdGenerator idGenerator,
                                   @Value("${agentcenter.runtime.default-type:OPENCODE}") String defaultRuntimeType) {
        this.workflowMapper = workflowMapper;
        this.workItemMapper = workItemMapper;
        this.artifactMapper = artifactMapper;
        this.confirmationMapper = confirmationMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentSessionService = agentSessionService;
        this.runtimeEventService = runtimeEventService;
        this.runtimeAdapter = runtimeAdapter;
        this.idGenerator = idGenerator;
        this.workflowRuntimeType = RuntimeType.valueOf(defaultRuntimeType.toUpperCase());
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
            scheduleResume(existing.getId());
            return buildResponse(existing);
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
        return buildResponse(instance);
    }

    public StartWorkflowResponse continueWorkflow(String instanceId) {
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found: " + instanceId);
        }

        List<WorkflowNodeInstanceEntity> nodes =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instanceId);

        String currentNodeId = instance.getCurrentNodeInstanceId();
        WorkflowNodeInstanceEntity currentNode = nodes.stream()
                .filter(n -> currentNodeId != null && currentNodeId.equals(n.getId()))
                .findFirst().orElse(null);

        if (currentNode != null
                && WorkflowNodeStatus.WAITING_CONFIRMATION.name().equals(currentNode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current node is waiting for confirmation. Resolve confirmation first.");
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

        node.setStatus(WorkflowNodeStatus.RUNNING.name());
        node.setStartedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        node.setErrorMessage(null);
        workflowMapper.updateNodeInstance(node);

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());

        executeSkillOnNode(instance, node, nodeDefs, workItem);

        instance = workflowMapper.findInstanceById(instance.getId());
        return buildResponse(instance);
    }

    public StartWorkflowResponse skipNode(String nodeInstanceId) {
        WorkflowNodeInstanceEntity node = findNodeInstanceOrThrow(nodeInstanceId);
        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(node.getWorkflowInstanceId());

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
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
        node.setStatus(WorkflowNodeStatus.COMPLETED.name());
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);

        advanceToNextNode(instance);

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

        String ensuredRuntimeSessionId = runtimeAdapter.ensureSession(workItem.getId(), session.id(), runtimeSessionId);
        if (!ensuredRuntimeSessionId.equals(runtimeSessionId)) {
            runtimeSessionId = ensuredRuntimeSessionId;
            agentSessionService.bindRuntimeSession(session.id(), runtimeSessionId, workflowRuntimeType);
        }

        node.setAgentSessionId(session.id());
        node.setRuntimeSessionId(runtimeSessionId);
        workflowMapper.updateNodeInstance(node);
        return session;
    }

    private void executeSkillOnNode(WorkflowInstanceEntity instance,
                                     WorkflowNodeInstanceEntity node,
                                     List<WorkflowNodeDefinitionEntity> nodeDefs,
                                     WorkItemEntity workItem) {
        WorkflowNodeDefinitionEntity nodeDef = nodeDefs.stream()
                .filter(d -> d.getId().equals(node.getNodeDefinitionId()))
                .findFirst().orElseThrow();

        String inputContext = buildInputContext(workItem, node);
        String skillName = nodeDef.getSkillName();
        insertWorkflowContextMessage(node.getAgentSessionId(), nodeDef, workItem, inputContext);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_STARTED, RuntimeEventSource.WORKFLOW,
                "{\"skillName\":\"" + skillName + "\"}", null
        ));

        SkillRunResult result = runtimeAdapter.runSkill(
                node.getRuntimeSessionId(), skillName, inputContext);

        ArtifactEntity artifact = null;
        if (result.success() && result.outputContent() != null) {
            artifact = new ArtifactEntity();
            artifact.setId(idGenerator.nextId());
            artifact.setWorkItemId(workItem.getId());
            artifact.setWorkflowInstanceId(instance.getId());
            artifact.setWorkflowNodeInstanceId(node.getId());
            artifact.setSessionId(node.getAgentSessionId());
            artifact.setArtifactType(result.artifactType());
            artifact.setTitle(skillName + " output");
            artifact.setContent(result.outputContent());
            artifact.setVersionNo(1);
            artifact.setCreatedBy("mock-runtime");
            artifact.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
            artifactMapper.insert(artifact);

            node.setOutputArtifactId(artifact.getId());
        }

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, node.getAgentSessionId(), workItem.getId(),
                instance.getId(), node.getId(),
                RuntimeEventType.SKILL_COMPLETED, RuntimeEventSource.WORKFLOW,
                "{\"skillName\":\"" + skillName + "\",\"success\":" + result.success() + "}", null
        ));

        String sessionId = node.getAgentSessionId();
        int nextSeqNo = nextMessageSeqNo(sessionId);
        String now = LocalDateTime.now().format(SQLITE_DATETIME);

        AgentMessageEntity toolMsg = new AgentMessageEntity();
        toolMsg.setId(idGenerator.nextId());
        toolMsg.setSessionId(sessionId);
        toolMsg.setRole("TOOL");
        toolMsg.setContent("Skill: " + skillName + " - " + (result.success() ? "completed" : "failed"));
        toolMsg.setContentFormat("TEXT");
        toolMsg.setStatus("COMPLETED");
        toolMsg.setSeqNo(nextSeqNo);
        toolMsg.setCreatedBy("workflow-engine");
        toolMsg.setCreatedAt(now);
        agentMessageMapper.insert(toolMsg);

        AgentMessageEntity assistantMsg = new AgentMessageEntity();
        assistantMsg.setId(idGenerator.nextId());
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(result.outputContent() != null ? result.outputContent() : "");
        assistantMsg.setContentFormat("MARKDOWN");
        assistantMsg.setStatus("COMPLETED");
        assistantMsg.setSeqNo(nextSeqNo + 1);
        assistantMsg.setCreatedBy("workflow-engine");
        assistantMsg.setCreatedAt(now);
        agentMessageMapper.insert(assistantMsg);

        boolean needsConfirmation = result.success()
                && Boolean.TRUE.equals(nodeDef.getRequiredConfirmation());

        if (needsConfirmation) {
            ConfirmationRequestEntity confirmation = new ConfirmationRequestEntity();
            confirmation.setId(idGenerator.nextId());
            confirmation.setRequestType(ConfirmationRequestType.CONFIRM.name());
            confirmation.setStatus(ConfirmationStatus.PENDING.name());
            confirmation.setWorkItemId(workItem.getId());
            confirmation.setWorkflowInstanceId(instance.getId());
            confirmation.setWorkflowNodeInstanceId(node.getId());
            confirmation.setAgentSessionId(node.getAgentSessionId());
            confirmation.setRuntimeType(workflowRuntimeType.name());
            confirmation.setRuntimeSessionId(node.getRuntimeSessionId());
            confirmation.setSkillName(skillName);
            confirmation.setTitle("确认节点输出: " + nodeDef.getName());
            confirmation.setContent(result.outputContent());
            confirmation.setContextSummary("请确认 " + nodeDef.getName() + " 节点的输出内容");
            confirmation.setOptionsJson("[\"approve\",\"reject\",\"modify\"]");
            confirmation.setPriority(Priority.MEDIUM.name());
            confirmation.setCreatedAt(now);
            confirmation.setUpdatedAt(now);
            confirmationMapper.insert(confirmation);

            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null, node.getAgentSessionId(), workItem.getId(),
                    instance.getId(), node.getId(),
                    RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                    "{\"confirmationId\":\"" + confirmation.getId() + "\"}", null
            ));

            node.setStatus(WorkflowNodeStatus.WAITING_CONFIRMATION.name());
        } else if (result.success()) {
            node.setStatus(WorkflowNodeStatus.COMPLETED.name());
            node.setCompletedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        } else {
            node.setStatus(WorkflowNodeStatus.FAILED.name());
            node.setErrorMessage(result.errorMessage());
        }

        workflowMapper.updateNodeInstance(node);
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

    private String findWorkflowRuntimeSessionId(WorkflowInstanceEntity instance) {
        return workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId()).stream()
                .map(WorkflowNodeInstanceEntity::getRuntimeSessionId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void insertWorkflowContextMessage(String sessionId,
                                              WorkflowNodeDefinitionEntity nodeDef,
                                              WorkItemEntity workItem,
                                              String inputContext) {
        AgentMessageEntity contextMsg = new AgentMessageEntity();
        contextMsg.setId(idGenerator.nextId());
        contextMsg.setSessionId(sessionId);
        contextMsg.setRole(MessageRole.SYSTEM.name());
        contextMsg.setContent("""
                已加载任务上下文

                - 工作项：%s %s
                - 类型：%s
                - 当前节点：%s
                - Skill：%s

                ```text
                %s
                ```
                """.formatted(
                workItem.getCode(),
                workItem.getTitle(),
                workItem.getType(),
                nodeDef.getName(),
                nodeDef.getSkillName(),
                inputContext
        ).trim());
        contextMsg.setContentFormat(ContentFormat.MARKDOWN.name());
        contextMsg.setStatus(MessageStatus.COMPLETED.name());
        contextMsg.setSeqNo(nextMessageSeqNo(sessionId));
        contextMsg.setCreatedBy("workflow-engine");
        contextMsg.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
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

    private void resumeAutoProgressIfPossible(WorkflowInstanceEntity instance) {
        List<WorkflowNodeInstanceEntity> nodes =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId());

        boolean isBlocked = nodes.stream()
                .anyMatch(n -> WorkflowNodeStatus.WAITING_CONFIRMATION.name().equals(n.getStatus())
                        || WorkflowNodeStatus.RUNNING.name().equals(n.getStatus()));
        if (isBlocked) {
            return;
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
        workflowExecutor.submit(() -> {
            try {
                WorkflowInstanceEntity instance = workflowMapper.findInstanceById(instanceId);
                if (instance != null) {
                    resumeAutoProgressIfPossible(instance);
                }
            } catch (Exception e) {
                log.error("Workflow auto-resume failed: instance={}", instanceId, e);
            }
        });
    }

    private void markNodeFailed(String nodeInstanceId, Exception error) {
        WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(nodeInstanceId);
        if (node == null) {
            return;
        }
        node.setStatus(WorkflowNodeStatus.FAILED.name());
        node.setErrorMessage(error.getMessage());
        node.setCompletedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        workflowMapper.updateNodeInstance(node);
    }

    private void advanceToNextNode(WorkflowInstanceEntity instance) {
        List<WorkflowNodeInstanceEntity> nodes =
                workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId());

        WorkflowNodeInstanceEntity nextPending = nodes.stream()
                .filter(n -> WorkflowNodeStatus.PENDING.name().equals(n.getStatus()))
                .findFirst().orElse(null);

        if (nextPending == null) {
            String now = LocalDateTime.now().format(SQLITE_DATETIME);
            instance.setStatus(WorkflowStatus.COMPLETED.name());
            instance.setCompletedAt(now);
            instance.setUpdatedAt(now);
            workflowMapper.updateInstance(instance);
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
                .findFirst().orElse(null);
    }

    private String buildInputContext(WorkItemEntity workItem, WorkflowNodeInstanceEntity node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Work Item: ").append(workItem.getTitle()).append("\n");
        sb.append("Type: ").append(workItem.getType()).append("\n");
        if (workItem.getDescription() != null) {
            sb.append("Description: ").append(workItem.getDescription()).append("\n");
        }
        if (node.getInputArtifactId() != null) {
            ArtifactEntity input = artifactMapper.findById(node.getInputArtifactId());
            if (input != null && input.getContent() != null) {
                sb.append("\nPrevious Output:\n").append(input.getContent());
            }
        }
        return sb.toString();
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
                e.getErrorMessage()
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
        return new ConfirmationRequestDto(
                e.getId(),
                ConfirmationRequestType.valueOf(e.getRequestType()),
                ConfirmationStatus.valueOf(e.getStatus()),
                e.getWorkItemId(),
                e.getWorkflowInstanceId(),
                e.getWorkflowNodeInstanceId(),
                e.getAgentSessionId(),
                e.getSkillName(),
                e.getTitle(),
                e.getContent(),
                e.getContextSummary(),
                e.getOptionsJson(),
                Priority.valueOf(e.getPriority()),
                parseDateTime(e.getCreatedAt())
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
}
