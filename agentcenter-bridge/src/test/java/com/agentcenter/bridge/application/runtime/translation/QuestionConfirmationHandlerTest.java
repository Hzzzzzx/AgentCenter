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
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
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
    private ConfirmationCreatedEventPayloadBuilder confirmationCreatedEventPayloadBuilder;
    private ObjectMapper objectMapper;
    private QuestionConfirmationHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        confirmationMapper = mock(ConfirmationMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        adapter = mock(OpenCodeRuntimeAdapter.class);
        adapterProvider = mock(ObjectProvider.class);
        confirmationCreatedEventPayloadBuilder = mock(ConfirmationCreatedEventPayloadBuilder.class);
        objectMapper = new ObjectMapper();
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        when(confirmationCreatedEventPayloadBuilder.buildPayload(any(ConfirmationRequestEntity.class)))
                .thenAnswer(invocation -> {
                    ConfirmationRequestEntity entity = invocation.getArgument(0);
                    return "{\"confirmationId\":\"" + entity.getId() + "\"}";
                });
        handler = new QuestionConfirmationHandler(confirmationMapper, runtimeEventService,
                adapterProvider, confirmationCreatedEventPayloadBuilder, objectMapper);
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
    void createQuestionConfirmationTreatsStringOptionsAsDecision() throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree("""
                {
                  "requestId": "q_string_options",
                  "questions": [
                    {
                      "header": "方案",
                      "question": "请选择推进方案",
                      "options": ["快速验证", "严格校验"]
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
        assertEquals(ConfirmationRequestType.DECISION.name(), entity.getRequestType());
        assertTrue(entity.getOptionsJson().contains("快速验证"));
        assertTrue(entity.getInteractionSchemaJson().contains("\"options\""));
    }

    @Test
    void createQuestionConfirmationPreservesOptionsForEachQuestionInMultiQuestionForm() throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree("""
                {
                  "requestId": "q_multi_options",
                  "questions": [
                    {
                      "header": "目标用户",
                      "question": "优先服务哪类用户？",
                      "options": ["产品负责人", "DevOps 工程师", "测试负责人"],
                      "custom": false
                    },
                    {
                      "header": "范围边界",
                      "question": "本次覆盖哪些范围？",
                      "options": ["只覆盖 PRD", "覆盖 PRD 和 HLD", "覆盖完整链路"],
                      "custom": false
                    },
                    {
                      "header": "验收标准",
                      "question": "用哪类标准证明完成？",
                      "options": ["UI 可见", "接口通过", "端到端通过"],
                      "custom": false
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

        assertEquals(ConfirmationRequestType.INPUT_REQUIRED.name(), entity.getRequestType());
        assertNull(entity.getOptionsJson());
        var schema = objectMapper.readTree(entity.getInteractionSchemaJson());
        assertEquals("需要你回答 3 个问题", schema.path("title").asText());
        assertEquals("select", schema.path("fields").get(0).path("type").asText());
        assertEquals("目标用户", schema.path("fields").get(0).path("label").asText());
        assertTrue(schema.path("fields").get(0).path("allowCustom").asBoolean());
        assertEquals("产品负责人", schema.path("fields").get(0).path("options").get(0).path("label").asText());
        assertEquals("select", schema.path("fields").get(1).path("type").asText());
        assertTrue(schema.path("fields").get(1).path("allowCustom").asBoolean());
        assertEquals("覆盖 PRD 和 HLD", schema.path("fields").get(1).path("options").get(1).path("value").asText());
        assertEquals("select", schema.path("fields").get(2).path("type").asText());
        assertTrue(schema.path("fields").get(2).path("allowCustom").asBoolean());
        assertEquals("端到端通过", schema.path("fields").get(2).path("options").get(2).path("label").asText());
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
    void respondQuestionSendsStructuredFieldAnswersInSchemaOrder() {
        ConfirmationRequestEntity entity = questionEntity(ConfirmationRequestType.INPUT_REQUIRED.name());
        entity.setInteractionSchemaJson("""
                {
                  "fields": [
                    {"id": "q0", "label": "问题 1"},
                    {"id": "q1", "label": "问题 2"}
                  ]
                }
                """);
        ResolveConfirmationRequest request = new ResolveConfirmationRequest(
                ConfirmationActionType.SUPPLEMENT,
                null,
                Map.of("fields", Map.of(
                        "q1", "验收标准覆盖登录失败",
                        "q0", "企业项目经理")));

        handler.respondQuestion(entity, request, ConfirmationActionType.SUPPLEMENT);

        verify(adapter).replyQuestion("ses_1", "q_1",
                List.of(List.of("企业项目经理"), List.of("验收标准覆盖登录失败")));
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
