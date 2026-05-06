package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConfirmationMapper {
    List<ConfirmationRequestEntity> findByStatus(@Param("status") String status);
    ConfirmationRequestEntity findById(@Param("id") String id);
    List<ConfirmationRequestEntity> findByWorkItemId(@Param("workItemId") String workItemId);
    void insert(ConfirmationRequestEntity entity);
    void update(ConfirmationRequestEntity entity);
}
