package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeResourceAuditEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeResourceAuditMapper {
    List<RuntimeResourceAuditEntity> findByProjectIdAndResourceId(
        @Param("projectId") String projectId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") String resourceId);
    void insert(RuntimeResourceAuditEntity entity);
}
