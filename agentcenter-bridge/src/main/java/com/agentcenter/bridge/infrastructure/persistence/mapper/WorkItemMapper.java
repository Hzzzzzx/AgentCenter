package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.WorkItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkItemMapper {
    List<WorkItemEntity> findAll();
    WorkItemEntity findById(@Param("id") String id);
    WorkItemEntity findByCode(@Param("code") String code);
    void insert(WorkItemEntity entity);
    void update(WorkItemEntity entity);
}
