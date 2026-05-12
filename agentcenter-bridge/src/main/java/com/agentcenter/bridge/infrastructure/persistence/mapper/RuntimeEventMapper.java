package com.agentcenter.bridge.infrastructure.persistence.mapper;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeEventMapper {
    List<RuntimeEventEntity> findBySessionId(@Param("sessionId") String sessionId);
    List<RuntimeEventEntity> findRecentBySessionId(@Param("sessionId") String sessionId,
                                                    @Param("limit") int limit);
    List<RuntimeEventEntity> findBySessionIdAfterSeq(@Param("sessionId") String sessionId,
                                                      @Param("afterSeq") long afterSeq,
                                                      @Param("limit") int limit);
    int nextSeqNo(@Param("sessionId") String sessionId);
    void insert(RuntimeEventEntity entity);
}
