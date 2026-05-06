package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeEventMapper {
    List<RuntimeEventEntity> findBySessionId(@Param("sessionId") String sessionId);
    void insert(RuntimeEventEntity entity);
}
