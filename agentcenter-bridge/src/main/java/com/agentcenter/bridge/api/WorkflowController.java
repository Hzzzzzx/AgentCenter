package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.api.dto.WorkflowDefinitionDto;
import com.agentcenter.bridge.api.dto.WorkflowInstanceDto;
import com.agentcenter.bridge.api.dto.WorkflowNodeDefinitionDto;
import com.agentcenter.bridge.application.WorkflowCommandService;
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.workflow.InputPolicy;
import com.agentcenter.bridge.domain.workflow.WorkflowDefinitionStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowMapper workflowMapper;

    public WorkflowController(WorkflowCommandService workflowCommandService,
                               WorkflowMapper workflowMapper) {
        this.workflowCommandService = workflowCommandService;
        this.workflowMapper = workflowMapper;
    }

    @GetMapping("/workflow-definitions")
    public List<WorkflowDefinitionDto> listDefinitions() {
        return workflowMapper.findAllDefinitions().stream()
                .map(d -> {
                    var nodes = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(d.getId())
                            .stream()
                            .map(nodeDef -> new WorkflowNodeDefinitionDto(
                                    nodeDef.getId(),
                                    nodeDef.getNodeKey(),
                                    nodeDef.getName(),
                                    nodeDef.getOrderNo() != null ? nodeDef.getOrderNo() : 0,
                                    nodeDef.getSkillName(),
                                    InputPolicy.valueOf(nodeDef.getInputPolicy()),
                                    nodeDef.getOutputArtifactType() != null
                                            ? ArtifactType.valueOf(nodeDef.getOutputArtifactType()) : null,
                                    Boolean.TRUE.equals(nodeDef.getRequiredConfirmation())
                            ))
                            .toList();
                    return new WorkflowDefinitionDto(
                            d.getId(),
                            WorkItemType.valueOf(d.getWorkItemType()),
                            d.getName(),
                            d.getVersionNo() != null ? d.getVersionNo() : 1,
                            WorkflowDefinitionStatus.valueOf(d.getStatus()),
                            Boolean.TRUE.equals(d.getIsDefault()),
                            nodes
                    );
                })
                .toList();
    }

    @GetMapping("/workflow-instances/{id}")
    public WorkflowInstanceDto getInstance(@PathVariable String id) {
        return workflowCommandService.getWorkflowInstance(id);
    }

    @PostMapping("/workflow-instances/{id}/continue")
    public StartWorkflowResponse continueWorkflow(@PathVariable String id) {
        return workflowCommandService.continueWorkflow(id);
    }

    @PostMapping("/workflow-node-instances/{id}/retry")
    public StartWorkflowResponse retryNode(@PathVariable String id) {
        return workflowCommandService.retryNode(id);
    }

    @PostMapping("/workflow-node-instances/{id}/skip")
    public StartWorkflowResponse skipNode(@PathVariable String id) {
        return workflowCommandService.skipNode(id);
    }
}
