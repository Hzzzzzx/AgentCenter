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
    List<WorkflowDefinitionEntity> findDefinitionsByProjectId(@Param("projectId") String projectId);
    WorkflowDefinitionEntity findDefinitionById(@Param("id") String id);
    List<WorkflowDefinitionEntity> findDefinitionsByWorkItemType(@Param("workItemType") String type);
    List<WorkflowDefinitionEntity> findDefinitionsByProjectIdAndWorkItemType(@Param("projectId") String projectId,
                                                                              @Param("workItemType") String type);
    WorkflowDefinitionEntity findDefaultEnabledDefinition();
    WorkflowDefinitionEntity findDefaultEnabledDefinitionByProjectId(@Param("projectId") String projectId);
    List<WorkflowNodeDefinitionEntity> findNodeDefinitionsByWorkflowDefinitionId(@Param("workflowDefinitionId") String id);
    void insertDefinition(WorkflowDefinitionEntity entity);
    void updateDefinition(WorkflowDefinitionEntity entity);
    void clearDefaultDefinitionsByWorkItemType(@Param("workItemType") String workItemType);
    void clearDefaultDefinitionsByProjectIdAndWorkItemType(@Param("projectId") String projectId,
                                                           @Param("workItemType") String workItemType);
    void insertNodeDefinition(WorkflowNodeDefinitionEntity entity);

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
    int renameSkillReferences(@Param("oldSkillName") String oldSkillName, @Param("newSkillName") String newSkillName);
    int renameNodeInstanceSkillName(@Param("oldSkillName") String oldSkillName, @Param("newSkillName") String newSkillName);
}
