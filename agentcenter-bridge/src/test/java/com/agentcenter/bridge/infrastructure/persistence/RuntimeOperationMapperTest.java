package com.agentcenter.bridge.infrastructure.persistence;

import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeOperationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RuntimeOperationMapperTest {

    @Autowired
    RuntimeOperationMapper mapper;

    @Autowired
    IdGenerator idGenerator;

    private RuntimeOperationEntity buildEntity() {
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId(idGenerator.nextId());
        entity.setProjectId("proj-001");
        entity.setRuntimeType("OPENCODE");
        entity.setOperationType("skill.install");
        entity.setStatus("CREATED");
        entity.setIdempotencyKey("idem-" + entity.getId());
        entity.setMessageId("msg-001");
        entity.setCorrelationId("corr-001");
        entity.setAgentSessionId("sess-001");
        entity.setRuntimeSessionId("rt-sess-001");
        entity.setWorkItemId("wi-001");
        entity.setWorkflowInstanceId("wf-001");
        entity.setWorkflowNodeInstanceId("wn-001");
        entity.setResourceType("skill");
        entity.setResourceId("skill-001");
        entity.setCommandJson("{\"action\":\"install\"}");
        entity.setAckJson(null);
        entity.setLastEventType("STATUS");
        entity.setLastEventId("evt-001");
        entity.setExternalStatus("PENDING");
        entity.setExternalOperationId("ext-001");
        entity.setErrorCode(null);
        entity.setErrorMessage(null);
        entity.setDeadlineAt("2026-05-08 12:00:00");
        entity.setStartedAt(null);
        entity.setCompletedAt(null);
        entity.setCreatedBy("tester");
        entity.setCreatedAt("2026-05-08 10:00:00");
        entity.setUpdatedAt("2026-05-08 10:00:00");
        return entity;
    }

    @Test
    void insertAndFindByIdRoundTrip() {
        RuntimeOperationEntity entity = buildEntity();
        mapper.insert(entity);

        RuntimeOperationEntity found = mapper.findById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getProjectId()).isEqualTo("proj-001");
        assertThat(found.getRuntimeType()).isEqualTo("OPENCODE");
        assertThat(found.getOperationType()).isEqualTo("skill.install");
        assertThat(found.getStatus()).isEqualTo("CREATED");
        assertThat(found.getIdempotencyKey()).isEqualTo(entity.getIdempotencyKey());
        assertThat(found.getCommandJson()).isEqualTo("{\"action\":\"install\"}");
        assertThat(found.getCreatedBy()).isEqualTo("tester");
    }

    @Test
    void findByIdempotencyKeyReturnsMatching() {
        RuntimeOperationEntity entity = buildEntity();
        mapper.insert(entity);

        RuntimeOperationEntity found = mapper.findByIdempotencyKey(
                entity.getProjectId(), entity.getRuntimeType(),
                entity.getOperationType(), entity.getIdempotencyKey());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(entity.getId());
    }

    @Test
    void findByIdempotencyKeyReturnsNullForNonExisting() {
        RuntimeOperationEntity found = mapper.findByIdempotencyKey(
                "nonexistent", "OPENCODE", "skill.install", "no-such-key");
        assertThat(found).isNull();
    }

    @Test
    void findByStatusReturnsMatching() {
        RuntimeOperationEntity entity = buildEntity();
        entity.setStatus("IN_PROGRESS");
        mapper.insert(entity);

        List<RuntimeOperationEntity> results = mapper.findByStatus("IN_PROGRESS");
        assertThat(results.stream().map(RuntimeOperationEntity::getId)).contains(entity.getId());
    }

    @Test
    void findByStatusReturnsEmptyForNonExisting() {
        List<RuntimeOperationEntity> results = mapper.findByStatus("TIMED_OUT");
        assertThat(results).isEmpty();
    }

    @Test
    void updateStatusChangesStatus() {
        RuntimeOperationEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.updateStatus(entity.getId(), "ACCEPTED", "2026-05-08 10:05:00");

        RuntimeOperationEntity updated = mapper.findById(entity.getId());
        assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
        assertThat(updated.getUpdatedAt()).isEqualTo("2026-05-08 10:05:00");
    }

    @Test
    void updateChangesMutableFields() {
        RuntimeOperationEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setStatus("SUCCEEDED");
        entity.setAckJson("{\"ack\":\"ok\"}");
        entity.setExternalStatus("COMPLETED");
        entity.setExternalOperationId("ext-done");
        entity.setStartedAt("2026-05-08 10:01:00");
        entity.setCompletedAt("2026-05-08 10:02:00");
        entity.setUpdatedAt("2026-05-08 10:02:00");
        mapper.update(entity);

        RuntimeOperationEntity updated = mapper.findById(entity.getId());
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(updated.getAckJson()).isEqualTo("{\"ack\":\"ok\"}");
        assertThat(updated.getExternalStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getExternalOperationId()).isEqualTo("ext-done");
        assertThat(updated.getStartedAt()).isEqualTo("2026-05-08 10:01:00");
        assertThat(updated.getCompletedAt()).isEqualTo("2026-05-08 10:02:00");
    }

    @Test
    void findStaleNonTerminalFindsPastDeadline() {
        RuntimeOperationEntity entity = buildEntity();
        entity.setStatus("IN_PROGRESS");
        entity.setDeadlineAt("2026-05-08 09:00:00");
        mapper.insert(entity);

        List<RuntimeOperationEntity> stale = mapper.findStaleNonTerminal("2026-05-08 10:00:00");
        assertThat(stale.stream().map(RuntimeOperationEntity::getId)).contains(entity.getId());
    }

    @Test
    void findStaleNonTerminalIgnoresTerminalOperations() {
        RuntimeOperationEntity entity = buildEntity();
        entity.setStatus("SUCCEEDED");
        entity.setDeadlineAt("2026-05-08 09:00:00");
        mapper.insert(entity);

        List<RuntimeOperationEntity> stale = mapper.findStaleNonTerminal("2026-05-08 10:00:00");
        assertThat(stale.stream().map(RuntimeOperationEntity::getId)).doesNotContain(entity.getId());
    }

    @Test
    void findStaleNonTerminalIgnoresNotYetPastDeadline() {
        RuntimeOperationEntity entity = buildEntity();
        entity.setStatus("IN_PROGRESS");
        entity.setDeadlineAt("2026-05-08 11:00:00");
        mapper.insert(entity);

        List<RuntimeOperationEntity> stale = mapper.findStaleNonTerminal("2026-05-08 10:00:00");
        assertThat(stale.stream().map(RuntimeOperationEntity::getId)).doesNotContain(entity.getId());
    }
}
