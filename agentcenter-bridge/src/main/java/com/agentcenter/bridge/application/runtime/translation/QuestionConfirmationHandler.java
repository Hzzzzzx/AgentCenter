package com.agentcenter.bridge.application.runtime.translation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEventEnvelope;
import com.agentcenter.bridge.domain.confirmation.ConfirmationActionType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeRuntimeAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class QuestionConfirmationHandler {
    public static final String INTERACTION_TYPE = "OPENCODE_QUESTION";

    private static final Logger log = LoggerFactory.getLogger(QuestionConfirmationHandler.class);
    private static final DateTimeFormatter SQLITE_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfirmationMapper confirmationMapper;
    private final RuntimeEventService runtimeEventService;
    private final ObjectProvider<OpenCodeRuntimeAdapter> runtimeAdapterProvider;
    private final ObjectMapper objectMapper;

    public QuestionConfirmationHandler(ConfirmationMapper confirmationMapper,
                                       RuntimeEventService runtimeEventService,
                                       ObjectProvider<OpenCodeRuntimeAdapter> runtimeAdapterProvider,
                                       ObjectMapper objectMapper) {
        this.confirmationMapper = confirmationMapper;
        this.runtimeEventService = runtimeEventService;
        this.runtimeAdapterProvider = runtimeAdapterProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional
    public void createQuestionConfirmation(RuntimeEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        if (payload == null || payload.isMissingNode()) {
            return;
        }

        String requestId = text(payload, "requestId");
        if (requestId.isBlank()) requestId = text(payload, "id");
        if (requestId.isBlank()) requestId = envelope.messageId();
        if (requestId == null || requestId.isBlank()) {
            log.warn("OpenCode question event missing request id for session {}", envelope.runtimeSessionId());
            return;
        }

        String confirmationId = confirmationIdFor(envelope.runtimeSessionId(), requestId);
        if (confirmationMapper.findById(confirmationId) != null) {
            log.info("Question confirmation already exists for requestId={}", requestId);
            return;
        }

        QuestionModel model = buildQuestionModel(requestId, payload);
        String now = LocalDateTime.now().format(SQLITE_DT);

        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId(confirmationId);
        entity.setRequestType(model.requestType());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setWorkItemId(envelope.workItemId());
        entity.setWorkflowInstanceId(envelope.workflowInstanceId());
        entity.setWorkflowNodeInstanceId(envelope.workflowNodeInstanceId());
        entity.setAgentSessionId(envelope.agentSessionId());
        entity.setRuntimeType(RuntimeType.OPENCODE.name());
        entity.setRuntimeSessionId(envelope.runtimeSessionId());
        entity.setRuntimeEventId(envelope.messageId());
        entity.setInteractionId(requestId);
        entity.setInteractionType(INTERACTION_TYPE);
        entity.setInteractionSchemaJson(model.schemaJson());
        entity.setInteractionContextJson(model.contextJson());
        entity.setInteractionRequired(1);
        entity.setSkillName(text(payload, "skillName"));
        entity.setTitle(model.title());
        entity.setContent(model.question());
        entity.setContextSummary(model.question());
        entity.setOptionsJson(model.optionsJson());
        entity.setPriority(Priority.HIGH.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        confirmationMapper.insert(entity);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, envelope.agentSessionId(), envelope.workItemId(), envelope.workflowInstanceId(),
                envelope.workflowNodeInstanceId(), RuntimeEventType.CONFIRMATION_CREATED,
                RuntimeEventSource.BRIDGE, confirmationCreatedPayload(entity), null
        ));
        log.info("Created OpenCode question confirmation id={} for agentSession={}",
                entity.getId(), envelope.agentSessionId());
    }

    public void respondQuestion(ConfirmationRequestEntity entity,
                                ResolveConfirmationRequest request,
                                ConfirmationActionType actionType) {
        OpenCodeRuntimeAdapter adapter = runtimeAdapterProvider.getIfAvailable();
        if (adapter == null) {
            throw new IllegalStateException("OpenCodeRuntimeAdapter not available for questionId="
                    + entity.getInteractionId());
        }

        if (ConfirmationActionType.REJECT.equals(actionType)) {
            adapter.rejectQuestion(entity.getRuntimeSessionId(), entity.getInteractionId());
            log.info("Rejected OpenCode question requestId={}", entity.getInteractionId());
            return;
        }

        List<List<String>> answers = buildAnswers(entity, request);
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("OpenCode question reply requires at least one answer");
        }
        adapter.replyQuestion(entity.getRuntimeSessionId(), entity.getInteractionId(), answers);
        log.info("Replied to OpenCode question requestId={} with {} answer group(s)",
                entity.getInteractionId(), answers.size());
    }

    public static boolean isQuestionConfirmation(ConfirmationRequestEntity entity) {
        return entity != null && INTERACTION_TYPE.equals(entity.getInteractionType());
    }

    public static String confirmationIdFor(String opencodeSessionId, String requestId) {
        String sessionPart = normalizeIdPart(opencodeSessionId, "session");
        String requestPart = normalizeIdPart(requestId, "question");
        return "question_" + sessionPart + "_" + requestPart;
    }

    private QuestionModel buildQuestionModel(String requestId, JsonNode payload) {
        List<QuestionItem> questions = parseQuestions(payload.path("questions"));
        if (questions.isEmpty()) {
            String fallbackQuestion = firstNonBlank(text(payload, "question"), text(payload, "label"),
                    "请回答当前问题");
            questions = List.of(new QuestionItem("q0", "问题", fallbackQuestion, List.of(), false, true));
        }

        boolean singleQuestion = questions.size() == 1;
        QuestionItem first = questions.get(0);
        boolean decision = singleQuestion && !first.options().isEmpty();
        String requestType = decision
                ? ConfirmationRequestType.DECISION.name()
                : ConfirmationRequestType.INPUT_REQUIRED.name();
        String title = singleQuestion
                ? firstNonBlank(first.header(), first.question(), "需要你回答问题")
                : "需要你回答 " + questions.size() + " 个问题";
        String question = singleQuestion
                ? first.question()
                : "请按问题分别补充答案";

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("id", requestId);
        schema.put("type", INTERACTION_TYPE);
        schema.put("title", title);
        schema.put("question", question);
        schema.put("required", true);
        schema.put("allowCustom", singleQuestion ? first.custom() : true);

        String optionsJson = null;
        if (decision) {
            schema.put("selection", first.multiple() ? "multi" : "single");
            ArrayNode options = optionArray(first.options());
            schema.set("options", options);
            optionsJson = options.toString();
        } else if (!singleQuestion) {
            ArrayNode fields = schema.putArray("fields");
            for (QuestionItem item : questions) {
                ObjectNode field = fields.addObject();
                field.put("id", item.id());
                field.put("label", firstNonBlank(item.header(), item.question(), item.id()));
                field.put("type", "textarea");
                field.put("required", true);
                field.put("placeholder", item.question());
            }
        }

        ObjectNode context = objectMapper.createObjectNode();
        context.put("requestId", requestId);
        context.set("questions", questionsArray(questions));
        String toolCallId = text(payload, "toolCallId");
        if (!toolCallId.isBlank()) context.put("toolCallId", toolCallId);
        String messageId = text(payload, "messageId");
        if (!messageId.isBlank()) context.put("messageId", messageId);

        return new QuestionModel(requestType, title, question, optionsJson,
                schema.toString(), context.toString());
    }

    private List<QuestionItem> parseQuestions(JsonNode questionsNode) {
        List<QuestionItem> result = new ArrayList<>();
        if (questionsNode == null || !questionsNode.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode node : questionsNode) {
            String question = firstNonBlank(text(node, "question"), text(node, "label"), "请回答当前问题");
            String header = firstNonBlank(text(node, "header"), "问题 " + (index + 1));
            String id = "q" + index;
            boolean multiple = node.path("multiple").asBoolean(false);
            boolean custom = !node.has("custom") || node.path("custom").asBoolean(true);
            result.add(new QuestionItem(id, header, question, parseOptions(node.path("options")), multiple, custom));
            index++;
        }
        return result;
    }

    private List<QuestionOption> parseOptions(JsonNode optionsNode) {
        List<QuestionOption> result = new ArrayList<>();
        if (optionsNode == null || !optionsNode.isArray()) {
            return result;
        }
        for (JsonNode node : optionsNode) {
            String label = node.isValueNode()
                    ? node.asText("").trim()
                    : firstNonBlank(text(node, "label"), text(node, "name"), text(node, "title"),
                    text(node, "value"), text(node, "id"));
            if (label.isBlank()) continue;
            String description = node.isObject() ? text(node, "description") : "";
            result.add(new QuestionOption(label, description));
        }
        return result;
    }

    private ArrayNode optionArray(List<QuestionOption> options) {
        ArrayNode array = objectMapper.createArrayNode();
        for (QuestionOption option : options) {
            ObjectNode node = array.addObject();
            node.put("id", option.label());
            node.put("label", option.label());
            if (!option.description().isBlank()) {
                node.put("description", option.description());
            }
        }
        return array;
    }

    private ArrayNode questionsArray(List<QuestionItem> questions) {
        ArrayNode array = objectMapper.createArrayNode();
        for (QuestionItem item : questions) {
            ObjectNode node = array.addObject();
            node.put("id", item.id());
            node.put("header", item.header());
            node.put("question", item.question());
            node.put("multiple", item.multiple());
            node.put("custom", item.custom());
            node.set("options", optionArray(item.options()));
        }
        return array;
    }

    private List<List<String>> buildAnswers(ConfirmationRequestEntity entity,
                                            ResolveConfirmationRequest request) {
        Map<String, Object> payload = request.payload();
        if (ConfirmationRequestType.DECISION.name().equals(entity.getRequestType())) {
            List<String> choiceAnswers = extractChoiceAnswers(payload, request.comment());
            return choiceAnswers.isEmpty() ? List.of() : List.of(choiceAnswers);
        }

        if (payload != null && payload.get("fields") instanceof Map<?, ?> fields) {
            List<String> fieldOrder = extractFieldOrder(entity);
            if (fieldOrder.isEmpty()) {
                return fields.values().stream()
                        .map(this::stringValue)
                        .filter(value -> !value.isBlank())
                        .map(List::of)
                        .toList();
            }
            List<List<String>> answers = new ArrayList<>();
            for (String fieldId : fieldOrder) {
                Object value = fields.get(fieldId);
                String text = stringValue(value);
                if (!text.isBlank()) {
                    answers.add(List.of(text));
                }
            }
            return answers;
        }

        String input = payload != null ? stringValue(payload.get("input")) : "";
        String answer = firstNonBlank(input, request.comment());
        return answer.isBlank() ? List.of() : List.of(List.of(answer));
    }

    private List<String> extractChoiceAnswers(Map<String, Object> payload, String comment) {
        if (payload == null) {
            String answer = firstNonBlank(comment);
            return answer.isBlank() ? List.of() : List.of(answer);
        }

        Object choices = payload.get("choices");
        if (choices instanceof Collection<?> collection) {
            List<String> values = collection.stream()
                    .map(this::stringValue)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (!values.isEmpty()) return values;
        }

        for (String key : List.of("customChoice", "choiceLabel", "choice")) {
            String value = stringValue(payload.get(key));
            if (!value.isBlank()) return List.of(value);
        }

        String fallback = firstNonBlank(comment);
        return fallback.isBlank() ? List.of() : List.of(fallback);
    }

    private List<String> extractFieldOrder(ConfirmationRequestEntity entity) {
        List<String> result = new ArrayList<>();
        try {
            JsonNode schema = objectMapper.readTree(entity.getInteractionSchemaJson());
            JsonNode fields = schema.path("fields");
            if (fields.isArray()) {
                for (JsonNode field : fields) {
                    String id = text(field, "id");
                    if (!id.isBlank()) result.add(id);
                }
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private String confirmationCreatedPayload(ConfirmationRequestEntity entity) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("confirmationId", entity.getId());
            payload.put("requestType", entity.getRequestType());
            payload.put("interactionType", entity.getInteractionType());
            payload.put("title", entity.getTitle());
            payload.put("question", entity.getContent());
            if (entity.getOptionsJson() != null && !entity.getOptionsJson().isBlank()) {
                payload.put("options", entity.getOptionsJson());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"confirmationId\":\"" + entity.getId() + "\"}";
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText("").trim();
    }

    private String stringValue(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeIdPart(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private record QuestionModel(String requestType, String title, String question,
                                 String optionsJson, String schemaJson, String contextJson) {}

    private record QuestionItem(String id, String header, String question,
                                List<QuestionOption> options, boolean multiple, boolean custom) {}

    private record QuestionOption(String label, String description) {}
}
