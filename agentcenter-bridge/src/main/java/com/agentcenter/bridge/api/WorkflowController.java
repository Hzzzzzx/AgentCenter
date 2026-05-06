package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.api.dto.UpdateWorkflowDefinitionRequest;
import com.agentcenter.bridge.api.dto.WorkflowDefinitionDto;
import com.agentcenter.bridge.api.dto.WorkflowInstanceDto;
import com.agentcenter.bridge.api.dto.WorkflowNodeDefinitionDto;
import com.agentcenter.bridge.application.WorkflowCommandService;
import com.agentcenter.bridge.domain.artifact.ArtifactType;
import com.agentcenter.bridge.domain.workflow.InputPolicy;
import com.agentcenter.bridge.domain.workflow.WorkflowDefinitionStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowMapper workflowMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public WorkflowController(WorkflowCommandService workflowCommandService,
                               WorkflowMapper workflowMapper,
                               IdGenerator idGenerator,
                               ObjectMapper objectMapper) {
        this.workflowCommandService = workflowCommandService;
        this.workflowMapper = workflowMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/workflow-definitions")
    public List<WorkflowDefinitionDto> listDefinitions() {
        return workflowMapper.findAllDefinitions().stream()
                .map(this::toDefinitionDto)
                .toList();
    }

    @PutMapping("/workflow-definitions/{id}")
    @Transactional
    public WorkflowDefinitionDto updateDefinition(@PathVariable String id,
                                                  @RequestBody UpdateWorkflowDefinitionRequest request) {
        WorkflowDefinitionEntity current = workflowMapper.findDefinitionById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
        }
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow definition must contain at least one node");
        }

        boolean isDefault = request.isDefault() != null
                ? request.isDefault()
                : Boolean.TRUE.equals(current.getIsDefault());
        if (isDefault) {
            workflowMapper.clearDefaultDefinitionsByWorkItemType(current.getWorkItemType());
        }

        current.setStatus(WorkflowDefinitionStatus.DISABLED.name());
        current.setIsDefault(false);
        workflowMapper.updateDefinition(current);

        WorkflowDefinitionEntity next = new WorkflowDefinitionEntity();
        next.setId(idGenerator.nextId());
        next.setWorkItemType(current.getWorkItemType());
        next.setName(nonBlank(request.name(), current.getName()));
        next.setVersionNo((current.getVersionNo() != null ? current.getVersionNo() : 1) + 1);
        next.setStatus(WorkflowDefinitionStatus.ENABLED.name());
        next.setIsDefault(isDefault);
        workflowMapper.insertDefinition(next);

        int order = 1;
        for (var node : request.nodes()) {
            workflowMapper.insertNodeDefinition(toNodeEntity(next.getId(), order, node));
            order++;
        }

        return toDefinitionDto(next);
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

    private WorkflowDefinitionDto toDefinitionDto(WorkflowDefinitionEntity definition) {
        var nodes = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(definition.getId())
                .stream()
                .map(this::toNodeDefinitionDto)
                .toList();
        return new WorkflowDefinitionDto(
                definition.getId(),
                WorkItemType.valueOf(definition.getWorkItemType()),
                definition.getName(),
                definition.getVersionNo() != null ? definition.getVersionNo() : 1,
                WorkflowDefinitionStatus.valueOf(definition.getStatus()),
                Boolean.TRUE.equals(definition.getIsDefault()),
                nodes
        );
    }

    private WorkflowNodeDefinitionDto toNodeDefinitionDto(WorkflowNodeDefinitionEntity nodeDef) {
        return new WorkflowNodeDefinitionDto(
                nodeDef.getId(),
                nodeDef.getNodeKey(),
                nodeDef.getName(),
                nodeDef.getOrderNo() != null ? nodeDef.getOrderNo() : 0,
                nodeDef.getSkillName(),
                InputPolicy.valueOf(nodeDef.getInputPolicy()),
                nodeDef.getOutputArtifactType() != null
                        ? ArtifactType.valueOf(nodeDef.getOutputArtifactType()) : null,
                Boolean.TRUE.equals(nodeDef.getRequiredConfirmation()),
                nodeDef.getStageKey(),
                nodeDef.getStageGoal(),
                nodeDef.getRecommendedSkillNamesJson(),
                Boolean.TRUE.equals(nodeDef.getAllowDynamicActions()),
                nodeDef.getConfirmationPolicy()
        );
    }

    private WorkflowNodeDefinitionEntity toNodeEntity(String definitionId,
                                                      int order,
                                                      UpdateWorkflowDefinitionRequest.Node request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Node name is required");
        }
        if (request.skillName() == null || request.skillName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Node skill is required");
        }

        WorkflowNodeDefinitionEntity node = new WorkflowNodeDefinitionEntity();
        node.setId(idGenerator.nextId());
        node.setWorkflowDefinitionId(definitionId);
        node.setNodeKey(nonBlank(request.nodeKey(), slugify(request.name(), order)));
        node.setName(request.name().trim());
        node.setOrderNo(order);
        node.setSkillName(request.skillName().trim());
        node.setInputPolicy((request.inputPolicy() != null ? request.inputPolicy() : InputPolicy.PREVIOUS_ARTIFACT).name());
        node.setOutputArtifactType((request.outputArtifactType() != null ? request.outputArtifactType() : ArtifactType.MARKDOWN).name());
        node.setOutputNameTemplate("%02d-%s.md".formatted(order, node.getNodeKey()));
        node.setRetryLimit(3);
        node.setTimeoutSeconds(300);
        node.setRequiredConfirmation(Boolean.TRUE.equals(request.requiredConfirmation()));
        node.setStageKey(nonBlank(request.stageKey(), node.getNodeKey()));
        node.setStageGoal(nonBlank(request.stageGoal(), node.getName()));
        node.setRecommendedSkillNamesJson(toRecommendedSkillsJson(request.recommendedSkillNames(), node.getSkillName()));
        node.setAllowDynamicActions(request.allowDynamicActions() == null || request.allowDynamicActions());
        node.setConfirmationPolicy(nonBlank(request.confirmationPolicy(),
                Boolean.TRUE.equals(request.requiredConfirmation()) ? "REQUIRED" : "AUTO"));
        return node;
    }

    private String toRecommendedSkillsJson(List<String> recommendedSkillNames, String fallbackSkillName) {
        List<String> names = recommendedSkillNames == null || recommendedSkillNames.isEmpty()
                ? List.of(fallbackSkillName)
                : recommendedSkillNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recommended skills", e);
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String slugify(String value, int order) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{Alnum}]+", "_")
                .replaceAll("(^_+|_+$)", "")
                .toLowerCase();
        return normalized.isBlank() ? "stage_" + order : normalized;
    }
}
