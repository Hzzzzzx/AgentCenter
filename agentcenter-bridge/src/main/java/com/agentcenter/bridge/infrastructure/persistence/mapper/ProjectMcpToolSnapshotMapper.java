package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectMcpToolSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMcpToolSnapshotMapper {
    List<ProjectMcpToolSnapshotEntity> findByMcpServerId(@Param("mcpServerId") String mcpServerId);
    List<ProjectMcpToolSnapshotEntity> findByProjectId(@Param("projectId") String projectId);
    void insert(ProjectMcpToolSnapshotEntity entity);
    void deleteByMcpServerId(@Param("mcpServerId") String mcpServerId);
    int countByMcpServerId(@Param("mcpServerId") String mcpServerId);
}
