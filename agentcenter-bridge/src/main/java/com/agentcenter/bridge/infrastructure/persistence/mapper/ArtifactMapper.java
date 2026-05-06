package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.ArtifactEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArtifactMapper {
    ArtifactEntity findById(@Param("id") String id);
    List<ArtifactEntity> findByWorkItemId(@Param("workItemId") String workItemId);
    List<ArtifactEntity> findByWorkflowNodeInstanceId(@Param("nodeInstanceId") String nodeInstanceId);
    void insert(ArtifactEntity entity);
}
