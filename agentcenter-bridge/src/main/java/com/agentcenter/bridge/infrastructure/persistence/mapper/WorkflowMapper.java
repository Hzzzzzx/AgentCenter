package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeDefinitionEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.WorkflowNodeInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowMapper {
    // Definitions
    List<WorkflowDefinitionEntity> findAllDefinitions();
    WorkflowDefinitionEntity findDefinitionById(@Param("id") String id);
    List<WorkflowDefinitionEntity> findDefinitionsByWorkItemType(@Param("workItemType") String type);
    WorkflowDefinitionEntity findDefaultEnabledDefinition();
    List<WorkflowNodeDefinitionEntity> findNodeDefinitionsByWorkflowDefinitionId(@Param("workflowDefinitionId") String id);

    // Instances
    WorkflowInstanceEntity findInstanceById(@Param("id") String id);
    List<WorkflowNodeInstanceEntity> findNodeInstancesByWorkflowInstanceId(@Param("workflowInstanceId") String id);
    void insertInstance(WorkflowInstanceEntity entity);
    void updateInstance(WorkflowInstanceEntity entity);
    void insertNodeInstance(WorkflowNodeInstanceEntity entity);
    void updateNodeInstance(WorkflowNodeInstanceEntity entity);
    WorkflowNodeInstanceEntity findNodeInstanceById(@Param("id") String id);
    List<WorkflowInstanceEntity> findInstancesByWorkItemId(@Param("workItemId") String workItemId);
    int countNodeDefinitionsBySkillName(@Param("skillName") String skillName);
}
