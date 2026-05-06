package com.agentcenter.bridge.application;

import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeResourceAuditEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeResourceAuditMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RuntimeResourceAuditService {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RuntimeResourceAuditMapper auditMapper;
    private final IdGenerator idGenerator;

    public RuntimeResourceAuditService(RuntimeResourceAuditMapper auditMapper, IdGenerator idGenerator) {
        this.auditMapper = auditMapper;
        this.idGenerator = idGenerator;
    }

    public void recordAudit(String projectId, String resourceType, String resourceId,
                            String action, String status, String summary,
                            String detailJson, String createdBy) {
        RuntimeResourceAuditEntity entity = new RuntimeResourceAuditEntity();
        entity.setId(idGenerator.nextId());
        entity.setProjectId(projectId);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setAction(action);
        entity.setStatus(status);
        entity.setSummary(summary);
        entity.setDetailJson(detailJson);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(LocalDateTime.now().format(SQLITE_DATETIME));
        auditMapper.insert(entity);
    }

    public List<RuntimeResourceAuditEntity> getAudits(String projectId, String resourceType, String resourceId) {
        return auditMapper.findByProjectIdAndResourceId(projectId, resourceType, resourceId);
    }
}
