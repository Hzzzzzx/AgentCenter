package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectContextEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectDataSyncHistoryEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectIterationEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectProviderSettingEntity;
import com.agentcenter.bridge.infrastructure.persistence.entity.ProjectSpaceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectContextMapper {
    ProjectProviderSettingEntity findSetting(@Param("id") String id);
    void insertSetting(ProjectProviderSettingEntity entity);
    void updateSetting(ProjectProviderSettingEntity entity);

    ProjectContextEntity findContextByProviderAndExternalProjectId(
            @Param("providerId") String providerId,
            @Param("externalProjectId") String externalProjectId);
    ProjectContextEntity findContextById(@Param("id") String id);
    void clearActiveContexts(@Param("providerId") String providerId);
    void insertContext(ProjectContextEntity entity);
    void updateContext(ProjectContextEntity entity);

    ProjectSpaceEntity findSpaceByProviderAndExternalSpaceId(
            @Param("providerId") String providerId,
            @Param("projectContextId") String projectContextId,
            @Param("externalSpaceId") String externalSpaceId);
    ProjectSpaceEntity findSpaceById(@Param("id") String id);
    void insertSpace(ProjectSpaceEntity entity);
    void updateSpace(ProjectSpaceEntity entity);

    ProjectIterationEntity findIterationByProviderAndExternalIterationId(
            @Param("providerId") String providerId,
            @Param("projectSpaceId") String projectSpaceId,
            @Param("externalIterationId") String externalIterationId);
    ProjectIterationEntity findIterationById(@Param("id") String id);
    void insertIteration(ProjectIterationEntity entity);
    void updateIteration(ProjectIterationEntity entity);

    void insertSyncHistory(ProjectDataSyncHistoryEntity entity);
    void updateSyncHistory(ProjectDataSyncHistoryEntity entity);
    List<ProjectDataSyncHistoryEntity> findSyncHistory(@Param("providerId") String providerId,
                                                       @Param("limit") int limit);
}
