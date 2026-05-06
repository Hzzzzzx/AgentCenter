package com.agentcenter.bridge.infrastructure.persistence;

import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.mapper.*;
import com.agentcenter.bridge.infrastructure.persistence.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PersistenceMapperIntegrationTest {

    @Autowired
    WorkItemMapper workItemMapper;
    @Autowired
    WorkflowMapper workflowMapper;
    @Autowired
    AgentSessionMapper sessionMapper;
    @Autowired
    AgentMessageMapper messageMapper;
    @Autowired
    RuntimeEventMapper eventMapper;
    @Autowired
    ArtifactMapper artifactMapper;
    @Autowired
    ConfirmationMapper confirmationMapper;
    @Autowired
    IdGenerator idGenerator;

    @Test
    void canReadSeededWorkItems() {
        List<WorkItemEntity> items = workItemMapper.findAll();
        assertThat(items).hasSizeGreaterThanOrEqualTo(3);
        assertThat(items.stream().map(WorkItemEntity::getCode)).contains("FE1234", "US1203", "BUG0602");
    }

    @Test
    void canReadWorkItemByCode() {
        WorkItemEntity item = workItemMapper.findByCode("FE1234");
        assertThat(item).isNotNull();
        assertThat(item.getTitle()).isEqualTo("用户登录优化");
        assertThat(item.getType()).isEqualTo("FE");
        assertThat(item.getStatus()).isEqualTo("BACKLOG");
    }

    @Test
    void canReadSeededWorkflowDefinition() {
        List<WorkflowDefinitionEntity> defs = workflowMapper.findAllDefinitions();
        assertThat(defs).isNotEmpty();

        List<WorkflowNodeDefinitionEntity> nodes = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(defs.get(0).getId());
        assertThat(nodes).hasSize(6);
    }

    @Test
    void canFindWorkflowDefinitionsByType() {
        List<WorkflowDefinitionEntity> feDefs = workflowMapper.findDefinitionsByWorkItemType("FE");
        assertThat(feDefs).isNotEmpty();
        assertThat(feDefs.get(0).getWorkItemType()).isEqualTo("FE");
    }

    @Test
    void canInsertAndReadSession() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(idGenerator.nextId());
        session.setSessionType("GENERAL");
        session.setTitle("Test Session");
        session.setRuntimeType("MOCK");
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);

        AgentSessionEntity found = sessionMapper.findById(session.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test Session");
        assertThat(found.getSessionType()).isEqualTo("GENERAL");
    }

    @Test
    void canInsertAndReadArtifact() {
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(idGenerator.nextId());
        artifact.setArtifactType("MARKDOWN");
        artifact.setTitle("Test Artifact");
        artifact.setContent("# Hello World");
        artifact.setVersionNo(1);
        artifactMapper.insert(artifact);

        ArtifactEntity found = artifactMapper.findById(artifact.getId());
        assertThat(found).isNotNull();
        assertThat(found.getContent()).isEqualTo("# Hello World");
        assertThat(found.getTitle()).isEqualTo("Test Artifact");
    }

    @Test
    void canInsertAndReadConfirmation() {
        ConfirmationRequestEntity conf = new ConfirmationRequestEntity();
        conf.setId(idGenerator.nextId());
        conf.setRequestType("CONFIRM");
        conf.setStatus("PENDING");
        conf.setTitle("Test Confirmation");
        conf.setPriority("MEDIUM");
        confirmationMapper.insert(conf);

        ConfirmationRequestEntity found = confirmationMapper.findById(conf.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test Confirmation");

        List<ConfirmationRequestEntity> pending = confirmationMapper.findByStatus("PENDING");
        assertThat(pending.stream().map(ConfirmationRequestEntity::getId)).contains(conf.getId());
    }

    @Test
    void canUpdateConfirmationStatus() {
        ConfirmationRequestEntity conf = new ConfirmationRequestEntity();
        conf.setId(idGenerator.nextId());
        conf.setRequestType("CONFIRM");
        conf.setStatus("PENDING");
        conf.setTitle("To Resolve");
        conf.setPriority("MEDIUM");
        confirmationMapper.insert(conf);

        conf.setStatus("RESOLVED");
        conf.setResolvedBy("tester");
        conf.setResolvedAt("2026-05-06 00:00:00");
        conf.setResolutionComment("Approved");
        confirmationMapper.update(conf);

        ConfirmationRequestEntity resolved = confirmationMapper.findById(conf.getId());
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getResolvedBy()).isEqualTo("tester");
    }

    @Test
    void canInsertRuntimeEvent() {
        RuntimeEventEntity evt = new RuntimeEventEntity();
        evt.setId(idGenerator.nextId());
        evt.setEventType("STATUS");
        evt.setEventSource("BRIDGE");
        evt.setPayloadJson("{\"status\":\"started\"}");
        eventMapper.insert(evt);

        List<RuntimeEventEntity> found = eventMapper.findBySessionId(null);
        assertThat(found).isNotNull();
    }

    @Test
    void canInsertAndReadMessage() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(idGenerator.nextId());
        session.setSessionType("GENERAL");
        session.setRuntimeType("MOCK");
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);

        AgentMessageEntity msg = new AgentMessageEntity();
        msg.setId(idGenerator.nextId());
        msg.setSessionId(session.getId());
        msg.setRole("USER");
        msg.setContent("Hello");
        msg.setContentFormat("TEXT");
        msg.setStatus("COMPLETED");
        msg.setSeqNo(1);
        messageMapper.insert(msg);

        List<AgentMessageEntity> msgs = messageMapper.findBySessionId(session.getId());
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getContent()).isEqualTo("Hello");
        assertThat(msgs.get(0).getSeqNo()).isEqualTo(1);
    }

    @Test
    void canInsertAndReadWorkflowInstance() {
        WorkItemEntity item = workItemMapper.findByCode("FE1234");
        List<WorkflowDefinitionEntity> defs = workflowMapper.findDefinitionsByWorkItemType("FE");
        assertThat(defs).isNotEmpty();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(idGenerator.nextId());
        instance.setWorkItemId(item.getId());
        instance.setWorkflowDefinitionId(defs.get(0).getId());
        instance.setStatus("PENDING");
        workflowMapper.insertInstance(instance);

        WorkflowInstanceEntity found = workflowMapper.findInstanceById(instance.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("PENDING");
        assertThat(found.getWorkItemId()).isEqualTo(item.getId());
    }

    @Test
    void canInsertAndReadWorkflowNodeInstance() {
        WorkItemEntity item = workItemMapper.findByCode("FE1234");
        List<WorkflowDefinitionEntity> defs = workflowMapper.findDefinitionsByWorkItemType("FE");
        List<WorkflowNodeDefinitionEntity> nodeDefs = workflowMapper.findNodeDefinitionsByWorkflowDefinitionId(defs.get(0).getId());

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(idGenerator.nextId());
        instance.setWorkItemId(item.getId());
        instance.setWorkflowDefinitionId(defs.get(0).getId());
        instance.setStatus("RUNNING");
        workflowMapper.insertInstance(instance);

        WorkflowNodeInstanceEntity nodeInstance = new WorkflowNodeInstanceEntity();
        nodeInstance.setId(idGenerator.nextId());
        nodeInstance.setWorkflowInstanceId(instance.getId());
        nodeInstance.setNodeDefinitionId(nodeDefs.get(0).getId());
        nodeInstance.setStatus("PENDING");
        nodeInstance.setVersion(1);
        workflowMapper.insertNodeInstance(nodeInstance);

        List<WorkflowNodeInstanceEntity> nodes = workflowMapper.findNodeInstancesByWorkflowInstanceId(instance.getId());
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getNodeDefinitionId()).isEqualTo(nodeDefs.get(0).getId());
    }
}
