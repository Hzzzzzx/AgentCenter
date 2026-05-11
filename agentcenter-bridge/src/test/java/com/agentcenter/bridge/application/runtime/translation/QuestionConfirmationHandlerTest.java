package com.agentcenter.bridge.application.runtime.translation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeRuntimeAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class QuestionConfirmationHandlerTest {

    private ConfirmationMapper confirmationMapper;
    private RuntimeEventService runtimeEventService;
    private OpenCodeRuntimeAdapter adapter;
    private ObjectProvider<OpenCodeRuntimeAdapter> adapterProvider;
    private ObjectMapper objectMapper;
    private QuestionConfirmationHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        confirmationMapper = mock(ConfirmationMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        adapter = mock(OpenCodeRuntimeAdapter.class);
        adapterProvider = mock(ObjectProvider.class);
        objectMapper = new ObjectMapper();
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        handler = new QuestionConfirmationHandler(confirmationMapper, runtimeEventService,
                adapterProvider, objectMapper);
    }

    @Test
    void createQuestionConfirmationStoresDecisionInteraction() throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree("""
                {
                  "requestId": "q_1",
                  "confirmationId": "question_ses_1_q_1",
                  "toolCallId": "call_q",
                  "questions": [
                    {
                      "header": "方案",
                      "question": "请选择推进方案",
                      "custom": true,
                      "options": [
                        {"label": "快速验证", "description": "先走最小验证"},
                        {"label": "严格校验", "description": "补充回归验证"}
                      ]
                    }
                  ]
                }
                """);
        RuntimeEventEnvelope envelope = new RuntimeEventEnvelope(
                "runtime-event", "question.requested", null, null, null,
                RuntimeType.OPENCODE, "agent-1", "ses_1", "work-1", "wf-1", "node-1",
                payload, OffsetDateTime.now());

        handler.createQuestionConfirmation(envelope);

        ArgumentCaptor<ConfirmationRequestEntity> entityCaptor =
                ArgumentCaptor.forClass(ConfirmationRequestEntity.class);
        verify(confirmationMapper).insert(entityCaptor.capture());
        ConfirmationRequestEntity entity = entityCaptor.getValue();
        assertEquals("question_ses_1_q_1", entity.getId());
        assertEquals(ConfirmationRequestType.DECISION.name(), entity.getRequestType());
        assertEquals(QuestionConfirmationHandler.INTERACTION_TYPE, entity.getInteractionType());
        assertEquals("请选择推进方案", entity.getContent());
        assertTrue(entity.getOptionsJson().contains("快速验证"));

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        RuntimeEventDto event = eventCaptor.getValue();
        assertEquals(RuntimeEventType.CONFIRMATION_CREATED, event.eventType());
        assertEquals("agent-1", event.sessionId());
        assertTrue(event.payloadJson().contains("question_ses_1_q_1"));
    }

    @Test
    void respondQuestionSendsChoiceLabelToOpenCode() {
        ConfirmationRequestEntity entity = questionEntity(ConfirmationRequestType.DECISION.name());
        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.CHOOSE,
                "快速验证",
                Map.of("choice", "FAST", "choiceLabel", "快速验证"));

        handler.respondQuestion(entity, request, ConfirmationActionType.CHOOSE);

        verify(adapter).replyQuestion("ses_1", "q_1", List.of(List.of("快速验证")));
    }

    @Test
    void respondQuestionRejectsQuestionInOpenCode() {
        ConfirmationRequestEntity entity = questionEntity(ConfirmationRequestType.INPUT_REQUIRED.name());
        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.REJECT, "稍后再说", null);

        handler.respondQuestion(entity, request, ConfirmationActionType.REJECT);

        verify(adapter).rejectQuestion("ses_1", "q_1");
        verify(adapter, never()).replyQuestion(any(), any(), any());
    }

    private ConfirmationRequestEntity questionEntity(String requestType) {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId("question_ses_1_q_1");
        entity.setRequestType(requestType);
        entity.setRuntimeSessionId("ses_1");
        entity.setInteractionId("q_1");
        entity.setInteractionType(QuestionConfirmationHandler.INTERACTION_TYPE);
        return entity;
    }
}
