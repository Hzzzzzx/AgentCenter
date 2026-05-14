package com.agentcenter.bridge.application.workflow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workflow.WorkflowNodeStatus;
import com.agentcenter.bridge.domain.workflow.WorkflowStatus;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkItemMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;

@Service
public class WorkflowRuntimeFailureService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String RUNTIME_EXCEPTION_INTERACTION = "RUNTIME_EXCEPTION";
    private static final String RUNTIME_EXCEPTION_OPTIONS_JSON =
            "[{\"value\":\"SUPPLEMENT\",\"label\":\"补充指令继续\"},"
                    + "{\"value\":\"RETRY\",\"label\":\"重试当前节点\"},"
                    + "{\"value\":\"SKIP\",\"label\":\"跳过当前节点\"},"
                    + "{\"value\":\"REJECT\",\"label\":\"取消恢复\"}]";

    private final WorkflowMapper workflowMapper;
    private final WorkItemMapper workItemMapper;
    private final ConfirmationMapper confirmationMapper;
    private final RuntimeEventService runtimeEventService;
    private final ConfirmationCreatedEventPayloadBuilder confirmationCreatedPayloadBuilder;
    private final IdGenerator idGenerator;

    public WorkflowRuntimeFailureService(WorkflowMapper workflowMapper,
                                         WorkItemMapper workItemMapper,
                                         ConfirmationMapper confirmationMapper,
                                         RuntimeEventService runtimeEventService,
                                         ConfirmationCreatedEventPayloadBuilder confirmationCreatedPayloadBuilder,
                                         IdGenerator idGenerator) {
        this.workflowMapper = workflowMapper;
        this.workItemMapper = workItemMapper;
        this.confirmationMapper = confirmationMapper;
        this.runtimeEventService = runtimeEventService;
        this.confirmationCreatedPayloadBuilder = confirmationCreatedPayloadBuilder;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public boolean blockNodeForRuntimeFailure(RuntimeFailureContext context) {
        if (context == null || isBlank(context.workflowInstanceId()) || isBlank(context.workflowNodeInstanceId())) {
            return false;
        }

        WorkflowInstanceEntity instance = workflowMapper.findInstanceById(context.workflowInstanceId());
        WorkflowNodeInstanceEntity node = workflowMapper.findNodeInstanceById(context.workflowNodeInstanceId());
        if (instance == null || node == null || !instance.getId().equals(node.getWorkflowInstanceId())) {
            return false;
        }
        if (isTerminalInstance(instance.getStatus()) || isTerminalNode(node.getStatus())) {
            return false;
        }

        WorkItemEntity workItem = workItemMapper.findById(instance.getWorkItemId());
        if (workItem == null) {
            return false;
        }

        String now = LocalDateTime.now().format(SQLITE_DATETIME);
        String detail = nonBlank(context.errorMessage(), "Runtime 执行异常，未返回明确错误原因。");

        node.setStatus(WorkflowNodeStatus.FAILED.name());
        node.setErrorMessage(detail);
        node.setCompletedAt(now);
        workflowMapper.updateNodeInstance(node);

        instance.setStatus(WorkflowStatus.BLOCKED.name());
        instance.setUpdatedAt(now);
        workflowMapper.updateInstance(instance);

        workItem.setUpdatedAt(now);
        workItemMapper.update(workItem);

        ConfirmationUpsertResult confirmationResult = upsertRuntimeExceptionConfirmation(
                instance, node, workItem, context, detail, now);
        if (confirmationResult.created()) {
            ConfirmationRequestEntity confirmation = confirmationResult.confirmation();
            runtimeEventService.publishEvent(new RuntimeEventDto(
                    null,
                    confirmation.getAgentSessionId(),
                    workItem.getId(),
                    instance.getId(),
                    node.getId(),
                    RuntimeEventType.CONFIRMATION_CREATED,
                    RuntimeEventSource.BRIDGE,
                    confirmationCreatedPayloadBuilder.buildPayload(confirmation),
                    null
            ));
        }
        return true;
    }

    private ConfirmationUpsertResult upsertRuntimeExceptionConfirmation(WorkflowInstanceEntity instance,
                                                                        WorkflowNodeInstanceEntity node,
                                                                        WorkItemEntity workItem,
                                                                        RuntimeFailureContext context,
                                                                        String detail,
                                                                        String now) {
        ConfirmationRequestEntity existing = findPendingException(workItem.getId(), node.getId());
        if (existing != null) {
            existing.setAgentSessionId(nonBlank(context.agentSessionId(), node.getAgentSessionId()));
            existing.setRuntimeType(context.runtimeType());
            existing.setRuntimeSessionId(nonBlank(context.runtimeSessionId(), node.getRuntimeSessionId()));
            existing.setSkillName(skillNameFor(node, instance, context));
            existing.setTitle(titleFor(workItem, node, instance, context));
            existing.setContent(contentFor(detail));
            existing.setContextSummary(contextSummary(node, instance, context));
            existing.setOptionsJson(RUNTIME_EXCEPTION_OPTIONS_JSON);
            existing.setPriority(Priority.HIGH.name());
            existing.setInteractionType(RUNTIME_EXCEPTION_INTERACTION);
            existing.setInteractionRequired(1);
            existing.setInteractionContextJson(contextJson(context, detail));
            existing.setUpdatedAt(now);
            confirmationMapper.updateRuntimeIntervention(existing);
            return new ConfirmationUpsertResult(existing, false);
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
        confirmation.setAgentSessionId(nonBlank(context.agentSessionId(), node.getAgentSessionId()));
        confirmation.setRuntimeType(context.runtimeType());
        confirmation.setRuntimeSessionId(nonBlank(context.runtimeSessionId(), node.getRuntimeSessionId()));
        confirmation.setSkillName(skillNameFor(node, instance, context));
        confirmation.setTitle(titleFor(workItem, node, instance, context));
        confirmation.setContent(contentFor(detail));
        confirmation.setContextSummary(contextSummary(node, instance, context));
        confirmation.setOptionsJson(RUNTIME_EXCEPTION_OPTIONS_JSON);
        confirmation.setPriority(Priority.HIGH.name());
        confirmation.setInteractionType(RUNTIME_EXCEPTION_INTERACTION);
        confirmation.setInteractionRequired(1);
        confirmation.setInteractionContextJson(contextJson(context, detail));
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.insert(confirmation);
        return new ConfirmationUpsertResult(confirmation, true);
    }

    private ConfirmationRequestEntity findPendingException(String workItemId, String nodeId) {
        return confirmationMapper.findByWorkItemId(workItemId).stream()
                .filter(confirmation -> nodeId.equals(confirmation.getWorkflowNodeInstanceId()))
                .filter(confirmation -> ConfirmationRequestType.EXCEPTION.name().equals(confirmation.getRequestType()))
                .filter(confirmation -> ConfirmationStatus.PENDING.name().equals(confirmation.getStatus())
                        || ConfirmationStatus.IN_CONVERSATION.name().equals(confirmation.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private boolean isTerminalInstance(String status) {
        return WorkflowStatus.COMPLETED.name().equals(status)
                || WorkflowStatus.CANCELLED.name().equals(status)
                || WorkflowStatus.SUPERSEDED.name().equals(status);
    }

    private boolean isTerminalNode(String status) {
        return WorkflowNodeStatus.COMPLETED.name().equals(status)
                || WorkflowNodeStatus.SKIPPED.name().equals(status);
    }

    private String titleFor(WorkItemEntity workItem,
                            WorkflowNodeInstanceEntity node,
                            WorkflowInstanceEntity instance,
                            RuntimeFailureContext context) {
        String nodeName = nodeNameFor(node, instance);
        String prefix = nonBlank(workItem.getCode(), "工作项");
        String title = nonBlank(context.title(), "Runtime 执行异常");
        return prefix + " " + nodeName + " · " + title;
    }

    private String contextSummary(WorkflowNodeInstanceEntity node,
                                  WorkflowInstanceEntity instance,
                                  RuntimeFailureContext context) {
        return "Runtime 异常已使工作流节点 %s（Skill：%s）暂停，流程等待用户处理。"
                .formatted(nodeNameFor(node, instance), skillNameFor(node, instance, context));
    }

    private String contentFor(String detail) {
        return """
                Runtime 执行异常，需要用户决定后续处理方式。

                失败原因：%s

                可补充指令继续、重试当前节点，或在确认不阻塞后续流程时跳过该节点。
                """.formatted(detail).trim();
    }

    private String contextJson(RuntimeFailureContext context, String detail) {
        return """
                {"failureCategory":"RUNTIME_EVENT","retryPolicy":"USER_CONFIRM","rawEventType":"%s","errorMessage":"%s"}
                """.formatted(jsonEscape(nonBlank(context.rawEventType(), "runtime.error")), jsonEscape(detail)).trim();
    }

    private String nodeNameFor(WorkflowNodeInstanceEntity node, WorkflowInstanceEntity instance) {
        WorkflowNodeDefinitionEntity nodeDef = nodeDefinitionFor(node, instance);
        return nonBlank(node.getSummary(), nodeDef != null ? nodeDef.getName() : nonBlank(node.getId(), "当前节点"));
    }

    private String skillNameFor(WorkflowNodeInstanceEntity node,
                                WorkflowInstanceEntity instance,
                                RuntimeFailureContext context) {
        WorkflowNodeDefinitionEntity nodeDef = nodeDefinitionFor(node, instance);
        return nonBlank(context.skillName(), nonBlank(node.getSkillName(),
                nodeDef != null ? nodeDef.getSkillName() : "unknown"));
    }

    private WorkflowNodeDefinitionEntity nodeDefinitionFor(WorkflowNodeInstanceEntity node,
                                                           WorkflowInstanceEntity instance) {
        if (instance == null || isBlank(instance.getWorkflowDefinitionId())) {
            return null;
        }
        List<WorkflowNodeDefinitionEntity> nodeDefs =
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(instance.getWorkflowDefinitionId());
        return nodeDefs.stream()
                .filter(definition -> definition.getId().equals(node.getNodeDefinitionId()))
                .findFirst()
                .orElse(null);
    }

    private String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public record RuntimeFailureContext(
            String workflowInstanceId,
            String workflowNodeInstanceId,
            String agentSessionId,
            String runtimeType,
            String runtimeSessionId,
            String skillName,
            String title,
            String errorMessage,
            String rawEventType,
            Map<String, Object> metadata
    ) {
    }

    private record ConfirmationUpsertResult(ConfirmationRequestEntity confirmation, boolean created) {
    }
}
