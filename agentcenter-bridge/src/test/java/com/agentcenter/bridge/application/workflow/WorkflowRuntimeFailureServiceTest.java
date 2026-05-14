package com.agentcenter.bridge.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
import com.agentcenter.bridge.application.workflow.WorkflowRuntimeFailureService.RuntimeFailureContext;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
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

class WorkflowRuntimeFailureServiceTest {

    private WorkflowMapper workflowMapper;
    private WorkItemMapper workItemMapper;
    private ConfirmationMapper confirmationMapper;
    private RuntimeEventService runtimeEventService;
    private ConfirmationCreatedEventPayloadBuilder confirmationCreatedEventPayloadBuilder;
    private WorkflowRuntimeFailureService service;

    @BeforeEach
    void setUp() {
        workflowMapper = mock(WorkflowMapper.class);
        workItemMapper = mock(WorkItemMapper.class);
        confirmationMapper = mock(ConfirmationMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        confirmationCreatedEventPayloadBuilder = mock(ConfirmationCreatedEventPayloadBuilder.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn("conf-1");
        when(confirmationCreatedEventPayloadBuilder.buildPayload(any())).thenReturn("{}");
        service = new WorkflowRuntimeFailureService(
                workflowMapper,
                workItemMapper,
                confirmationMapper,
                runtimeEventService,
                confirmationCreatedEventPayloadBuilder,
                idGenerator);
    }

    @Test
    void blockNodeForRuntimeFailureMarksNodeFailedAndCreatesException() {
        WorkflowInstanceEntity instance = workflowInstance();
        WorkflowNodeInstanceEntity node = workflowNode(WorkflowNodeStatus.RUNNING.name());
        WorkItemEntity workItem = workItem();
        WorkflowNodeDefinitionEntity nodeDef = nodeDefinition();

        when(workflowMapper.findInstanceById("wf-1")).thenReturn(instance);
        when(workflowMapper.findNodeInstanceById("node-1")).thenReturn(node);
        when(workItemMapper.findById("work-1")).thenReturn(workItem);
        when(workflowMapper.findNodeDefinitionsByWorkflowDefinitionId("def-1")).thenReturn(List.of(nodeDef));
        when(confirmationMapper.findByWorkItemId("work-1")).thenReturn(List.of());

        boolean blocked = service.blockNodeForRuntimeFailure(new RuntimeFailureContext(
                "wf-1", "node-1", "agent-1", "OPENCODE", "rt-1", null,
                "OpenCode session error", "tool crashed", "session.error", null));

        assertThat(blocked).isTrue();
        assertThat(node.getStatus()).isEqualTo(WorkflowNodeStatus.FAILED.name());
        assertThat(node.getErrorMessage()).isEqualTo("tool crashed");
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.BLOCKED.name());

        ArgumentCaptor<ConfirmationRequestEntity> confirmationCaptor =
                ArgumentCaptor.forClass(ConfirmationRequestEntity.class);
        verify(confirmationMapper).insert(confirmationCaptor.capture());
        ConfirmationRequestEntity confirmation = confirmationCaptor.getValue();
        assertThat(confirmation.getRequestType()).isEqualTo(ConfirmationRequestType.EXCEPTION.name());
        assertThat(confirmation.getStatus()).isEqualTo(ConfirmationStatus.PENDING.name());
        assertThat(confirmation.getWorkflowNodeInstanceId()).isEqualTo("node-1");
        assertThat(confirmation.getInteractionType()).isEqualTo("RUNTIME_EXCEPTION");
        assertThat(confirmation.getContent()).contains("tool crashed");
        verify(runtimeEventService).publishEvent(any(RuntimeEventDto.class));
    }

    @Test
    void blockNodeForRuntimeFailureIgnoresCompletedNode() {
        WorkflowInstanceEntity instance = workflowInstance();
        WorkflowNodeInstanceEntity node = workflowNode(WorkflowNodeStatus.COMPLETED.name());

        when(workflowMapper.findInstanceById("wf-1")).thenReturn(instance);
        when(workflowMapper.findNodeInstanceById("node-1")).thenReturn(node);

        boolean blocked = service.blockNodeForRuntimeFailure(new RuntimeFailureContext(
                "wf-1", "node-1", "agent-1", "OPENCODE", "rt-1", null,
                "OpenCode session error", "late error", "session.error", null));

        assertThat(blocked).isFalse();
        verify(workflowMapper, never()).updateNodeInstance(any());
        verify(confirmationMapper, never()).insert(any());
    }

    private WorkflowInstanceEntity workflowInstance() {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId("wf-1");
        instance.setWorkItemId("work-1");
        instance.setWorkflowDefinitionId("def-1");
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setCurrentNodeInstanceId("node-1");
        return instance;
    }

    private WorkflowNodeInstanceEntity workflowNode(String status) {
        WorkflowNodeInstanceEntity node = new WorkflowNodeInstanceEntity();
        node.setId("node-1");
        node.setWorkflowInstanceId("wf-1");
        node.setNodeDefinitionId("node-def-1");
        node.setStatus(status);
        node.setAgentSessionId("agent-1");
        node.setRuntimeSessionId("rt-1");
        node.setSkillName("prd-design");
        return node;
    }

    private WorkflowNodeDefinitionEntity nodeDefinition() {
        WorkflowNodeDefinitionEntity nodeDef = new WorkflowNodeDefinitionEntity();
        nodeDef.setId("node-def-1");
        nodeDef.setName("需求整理");
        nodeDef.setSkillName("prd-design");
        return nodeDef;
    }

    private WorkItemEntity workItem() {
        WorkItemEntity workItem = new WorkItemEntity();
        workItem.setId("work-1");
        workItem.setCode("FE1234");
        workItem.setTitle("测试工作项");
        workItem.setStatus("IN_PROGRESS");
        workItem.setPriority("HIGH");
        workItem.setVersion(1);
        return workItem;
    }
}
