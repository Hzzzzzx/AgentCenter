package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectMcpServerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMcpServerMapper {
    List<ProjectMcpServerEntity> findByProjectId(@Param("projectId") String projectId);
    ProjectMcpServerEntity findById(@Param("id") String id);
    ProjectMcpServerEntity findByProjectIdAndName(@Param("projectId") String projectId, @Param("name") String name);
    void insert(ProjectMcpServerEntity entity);
    void update(ProjectMcpServerEntity entity);
    void deleteById(@Param("id") String id);
}
