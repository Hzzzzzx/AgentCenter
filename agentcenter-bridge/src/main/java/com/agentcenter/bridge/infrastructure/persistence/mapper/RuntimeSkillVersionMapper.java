package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeSkillVersionMapper {
    List<RuntimeSkillVersionEntity> findBySkillId(@Param("skillId") String skillId);
    RuntimeSkillVersionEntity findById(@Param("id") String id);
    RuntimeSkillVersionEntity findActiveBySkillId(@Param("skillId") String skillId);
    void insert(RuntimeSkillVersionEntity entity);
    void updateStatus(@Param("id") String id, @Param("status") String status);
}
