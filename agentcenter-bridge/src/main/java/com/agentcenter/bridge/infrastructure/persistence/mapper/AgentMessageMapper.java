package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentMessageMapper {
    List<AgentMessageEntity> findBySessionId(@Param("sessionId") String sessionId);
    void insert(AgentMessageEntity entity);
}
