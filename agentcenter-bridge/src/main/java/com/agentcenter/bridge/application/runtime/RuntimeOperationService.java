package com.agentcenter.bridge.application.runtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeOperationType;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeOperationMapper;

@Service
public class RuntimeOperationService {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<RuntimeOperationStatus, Set<RuntimeOperationStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<RuntimeOperationStatus, Set<RuntimeOperationStatus>> map = new EnumMap<>(RuntimeOperationStatus.class);
        map.put(RuntimeOperationStatus.CREATED, EnumSet.of(RuntimeOperationStatus.DISPATCHING, RuntimeOperationStatus.TIMED_OUT));
        map.put(RuntimeOperationStatus.DISPATCHING, EnumSet.of(RuntimeOperationStatus.ACCEPTED, RuntimeOperationStatus.FAILED, RuntimeOperationStatus.SUCCEEDED, RuntimeOperationStatus.TIMED_OUT));
        map.put(RuntimeOperationStatus.ACCEPTED, EnumSet.of(RuntimeOperationStatus.IN_PROGRESS, RuntimeOperationStatus.SUCCEEDED, RuntimeOperationStatus.FAILED, RuntimeOperationStatus.TIMED_OUT));
        map.put(RuntimeOperationStatus.IN_PROGRESS, EnumSet.of(RuntimeOperationStatus.SUCCEEDED, RuntimeOperationStatus.FAILED, RuntimeOperationStatus.CANCELED, RuntimeOperationStatus.TIMED_OUT));
        // Terminal states have no allowed outgoing transitions — absent from map
        ALLOWED_TRANSITIONS = Map.copyOf(map);
    }

    private final RuntimeOperationMapper mapper;
    private final IdGenerator idGenerator;

    public RuntimeOperationService(RuntimeOperationMapper mapper, IdGenerator idGenerator) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    public RuntimeOperationEntity createOperation(String projectId,
                                                   String runtimeType,
                                                   String operationType,
                                                   String idempotencyKey,
                                                   String messageId,
                                                   String correlationId,
                                                   String agentSessionId,
                                                   String runtimeSessionId,
                                                   String workItemId,
                                                   String workflowInstanceId,
                                                   String workflowNodeInstanceId,
                                                   String resourceType,
                                                   String resourceId,
                                                   String commandJson,
                                                   String deadlineAt,
                                                   String createdBy) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RuntimeOperationEntity existing = mapper.findByIdempotencyKey(
                    projectId, runtimeType, operationType, idempotencyKey);
            if (existing != null) {
                return existing;
            }
        }

        RuntimeOperationType.fromValue(operationType);

        String now = now();
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId(idGenerator.nextId());
        entity.setProjectId(projectId);
        entity.setRuntimeType(runtimeType);
        entity.setOperationType(operationType);
        entity.setStatus(RuntimeOperationStatus.CREATED.name());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setMessageId(messageId);
        entity.setCorrelationId(correlationId);
        entity.setAgentSessionId(agentSessionId);
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setWorkItemId(workItemId);
        entity.setWorkflowInstanceId(workflowInstanceId);
        entity.setWorkflowNodeInstanceId(workflowNodeInstanceId);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setCommandJson(commandJson);
        entity.setDeadlineAt(deadlineAt);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        mapper.insert(entity);
        return entity;
    }

    public void transition(String operationId, RuntimeOperationStatus targetStatus) {
        RuntimeOperationEntity entity = findOrThrow(operationId);
        RuntimeOperationStatus current = RuntimeOperationStatus.valueOf(entity.getStatus());

        if (current.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + targetStatus);
        }

        Set<RuntimeOperationStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + targetStatus);
        }

        entity.setStatus(targetStatus.name());
        entity.setUpdatedAt(now());

        if (targetStatus.isTerminal()) {
            entity.setCompletedAt(now());
        }

        mapper.update(entity);
    }

    public void transitionToFailed(String operationId, String errorCode, String errorMessage) {
        RuntimeOperationEntity entity = findOrThrow(operationId);
        RuntimeOperationStatus current = RuntimeOperationStatus.valueOf(entity.getStatus());

        if (current.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + RuntimeOperationStatus.FAILED);
        }

        Set<RuntimeOperationStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(RuntimeOperationStatus.FAILED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + RuntimeOperationStatus.FAILED);
        }

        String now = now();
        entity.setStatus(RuntimeOperationStatus.FAILED.name());
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(now);
        entity.setCompletedAt(now);

        mapper.update(entity);
    }

    public RuntimeOperationEntity findById(String id) {
        return mapper.findById(id);
    }

    public RuntimeOperationEntity findByIdempotencyKey(String projectId,
                                                        String runtimeType,
                                                        String operationType,
                                                        String idempotencyKey) {
        return mapper.findByIdempotencyKey(projectId, runtimeType, operationType, idempotencyKey);
    }

    public List<RuntimeOperationEntity> findStaleOperations() {
        return mapper.findStaleNonTerminal(now());
    }

    public int timeoutStaleOperations() {
        List<RuntimeOperationEntity> stale = findStaleOperations();
        int count = 0;
        for (RuntimeOperationEntity entity : stale) {
            try {
                transition(entity.getId(), RuntimeOperationStatus.TIMED_OUT);
                count++;
            } catch (IllegalStateException ignored) {
                // Already transitioned by another process — skip
            }
        }
        return count;
    }

    private RuntimeOperationEntity findOrThrow(String operationId) {
        RuntimeOperationEntity entity = mapper.findById(operationId);
        if (entity == null) {
            throw new IllegalArgumentException("Operation not found: " + operationId);
        }
        return entity;
    }

    private String now() {
        return LocalDateTime.now().format(TS_FORMAT);
    }
}
