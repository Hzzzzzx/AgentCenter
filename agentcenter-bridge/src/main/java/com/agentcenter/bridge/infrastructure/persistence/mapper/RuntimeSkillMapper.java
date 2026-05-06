package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeSkillMapper {
    List<RuntimeSkillEntity> findByProjectId(@Param("projectId") String projectId);
    RuntimeSkillEntity findById(@Param("id") String id);
    RuntimeSkillEntity findByProjectIdAndName(@Param("projectId") String projectId, @Param("name") String name);
    void insert(RuntimeSkillEntity entity);
    void update(RuntimeSkillEntity entity);
    void deleteById(@Param("id") String id);
    int countByProjectId(@Param("projectId") String projectId);
}
