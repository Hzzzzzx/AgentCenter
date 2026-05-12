package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.application.ProjectDefaults;
import com.agentcenter.bridge.domain.workflow.WorkflowDefinitionStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class ProjectWorkflowProvisioningService {

    private final WorkflowMapper workflowMapper;
    private final IdGenerator idGenerator;

    public ProjectWorkflowProvisioningService(WorkflowMapper workflowMapper, IdGenerator idGenerator) {
        this.workflowMapper = workflowMapper;
        this.idGenerator = idGenerator;
    }

    public void ensureFeWorkflowForProjects(Collection<String> projectIds) {
        for (String projectId : projectIds) {
            ensureFeWorkflowForProject(projectId);
        }
    }

    private void ensureFeWorkflowForProject(String projectId) {
        if (projectId == null || projectId.isBlank()
                || ProjectDefaults.DEFAULT_PROJECT_ID.equals(projectId)) {
            return;
        }
        if (!workflowMapper.findDefinitionsByProjectIdAndWorkItemType(
                projectId, WorkItemType.FE.name()).isEmpty()) {
            return;
        }

        WorkflowDefinitionEntity template = defaultFeTemplate();
        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setId(idGenerator.nextId());
        definition.setProjectId(projectId);
        definition.setWorkItemType(template.getWorkItemType());
        definition.setName(template.getName());
        definition.setVersionNo(template.getVersionNo());
        definition.setStatus(template.getStatus());
        definition.setIsDefault(true);
        workflowMapper.insertDefinition(definition);

        for (WorkflowNodeDefinitionEntity templateNode :
                workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(template.getId())) {
            workflowMapper.insertNodeDefinition(copyNode(templateNode, definition.getId()));
        }
    }

    private WorkflowDefinitionEntity defaultFeTemplate() {
        return workflowMapper.findDefinitionsByProjectIdAndWorkItemType(
                        ProjectDefaults.DEFAULT_PROJECT_ID, WorkItemType.FE.name())
                .stream()
                .filter(def -> WorkflowDefinitionStatus.ENABLED.name().equals(def.getStatus()))
                .filter(def -> Boolean.TRUE.equals(def.getIsDefault()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No default FE workflow template found"));
    }

    private WorkflowNodeDefinitionEntity copyNode(WorkflowNodeDefinitionEntity templateNode,
                                                  String workflowDefinitionId) {
        WorkflowNodeDefinitionEntity node = new WorkflowNodeDefinitionEntity();
        node.setId(idGenerator.nextId());
        node.setWorkflowDefinitionId(workflowDefinitionId);
        node.setNodeKey(templateNode.getNodeKey());
        node.setName(templateNode.getName());
        node.setOrderNo(templateNode.getOrderNo());
        node.setSkillName(templateNode.getSkillName());
        node.setInputPolicy(templateNode.getInputPolicy());
        node.setOutputArtifactType(templateNode.getOutputArtifactType());
        node.setOutputNameTemplate(templateNode.getOutputNameTemplate());
        node.setRetryLimit(templateNode.getRetryLimit());
        node.setTimeoutSeconds(templateNode.getTimeoutSeconds());
        node.setRequiredConfirmation(templateNode.getRequiredConfirmation());
        node.setStageKey(templateNode.getStageKey());
        node.setStageGoal(templateNode.getStageGoal());
        node.setRecommendedSkillNamesJson(templateNode.getRecommendedSkillNamesJson());
        node.setAllowDynamicActions(templateNode.getAllowDynamicActions());
        node.setConfirmationPolicy(templateNode.getConfirmationPolicy());
        return node;
    }
}
