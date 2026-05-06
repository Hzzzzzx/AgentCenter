package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.AgentSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentSessionMapper {
    List<AgentSessionEntity> findAll();
    AgentSessionEntity findById(@Param("id") String id);
    List<AgentSessionEntity> findByWorkItemId(@Param("workItemId") String workItemId);
    void insert(AgentSessionEntity entity);
    void update(AgentSessionEntity entity);
}
