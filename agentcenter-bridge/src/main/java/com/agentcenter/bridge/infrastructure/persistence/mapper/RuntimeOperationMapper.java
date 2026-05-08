package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeOperationMapper {
    void insert(RuntimeOperationEntity entity);
    RuntimeOperationEntity findById(@Param("id") String id);
    RuntimeOperationEntity findByIdempotencyKey(
        @Param("projectId") String projectId,
        @Param("runtimeType") String runtimeType,
        @Param("operationType") String operationType,
        @Param("idempotencyKey") String idempotencyKey);
    List<RuntimeOperationEntity> findByStatus(@Param("status") String status);
    List<RuntimeOperationEntity> findStaleNonTerminal(@Param("now") String now);
    void updateStatus(@Param("id") String id, @Param("status") String status, @Param("updatedAt") String updatedAt);
    void update(RuntimeOperationEntity entity);
}
