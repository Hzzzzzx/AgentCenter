package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;

class WorkflowContextAnchorServiceTest {

    @Test
    void requiresRecoveryWhenCompactionIsNewerThanPromptDebug() {
        RuntimeEventMapper eventMapper = mock(RuntimeEventMapper.class);
        when(eventMapper.findRecentBySessionId("sess-1", 160)).thenReturn(List.of(
                processTrace("node-1", 9, "{\"kind\":\"prompt_debug\"}"),
                processTrace("node-1", 10, "{\"kind\":\"compaction\",\"rawPartType\":\"compaction\"}")
        ));
        WorkflowContextAnchorService service = new WorkflowContextAnchorService(
                eventMapper, mock(RuntimeEventService.class));

        WorkflowContextAnchorService.ContextAnchorDecision decision = service.decide("sess-1", "node-1");

        assertThat(decision.required()).isTrue();
        assertThat(service.inputSection(decision))
                .contains("## AGENTCENTER_CONTEXT_ANCHOR")
                .contains("RECOVERED_AFTER_OPENCODE_COMPACTION")
                .contains("最近压缩事件序号：10");
    }

    @Test
    void skipsRecoveryWhenContextAnchorIsNewerThanCompaction() {
        RuntimeEventMapper eventMapper = mock(RuntimeEventMapper.class);
        when(eventMapper.findRecentBySessionId("sess-1", 160)).thenReturn(List.of(
                processTrace("node-1", 10, "{\"kind\":\"compaction\",\"rawPartType\":\"compaction\"}"),
                processTrace("node-1", 11, "{\"kind\":\"context_anchor\"}")
        ));
        WorkflowContextAnchorService service = new WorkflowContextAnchorService(
                eventMapper, mock(RuntimeEventService.class));

        WorkflowContextAnchorService.ContextAnchorDecision decision = service.decide("sess-1", "node-1");

        assertThat(decision.required()).isFalse();
        assertThat(service.inputSection(decision)).isEmpty();
    }

    @Test
    void publishesPublicSummaryTraceForInjectedAnchor() {
        RuntimeEventMapper eventMapper = mock(RuntimeEventMapper.class);
        RuntimeEventService runtimeEventService = mock(RuntimeEventService.class);
        WorkflowContextAnchorService service = new WorkflowContextAnchorService(eventMapper, runtimeEventService);

        service.publishContextAnchor(
                new WorkflowContextAnchorService.ContextAnchorDecision(true, 10, 9),
                "sess-1", "work-1", "wf-1", "node-1");

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        RuntimeEventDto event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(RuntimeEventType.PROCESS_TRACE);
        assertThat(event.eventSource()).isEqualTo(RuntimeEventSource.WORKFLOW);
        assertThat(event.payloadJson())
                .contains("\"kind\":\"context_anchor\"")
                .contains("已恢复工作流上下文")
                .contains("opencode_compaction");
    }

    private RuntimeEventEntity processTrace(String nodeInstanceId, int seqNo, String payloadJson) {
        RuntimeEventEntity event = new RuntimeEventEntity();
        event.setSessionId("sess-1");
        event.setWorkflowNodeInstanceId(nodeInstanceId);
        event.setEventType(RuntimeEventType.PROCESS_TRACE.name());
        event.setSeqNo(seqNo);
        event.setPayloadJson(payloadJson);
        return event;
    }
}
