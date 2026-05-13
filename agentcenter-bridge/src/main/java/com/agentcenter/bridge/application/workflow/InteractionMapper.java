package com.agentcenter.bridge.application.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteraction;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteractionType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps between protocol-level interactions and confirmation_request persistence entities.
 * Phase 1 reuses the confirmation_request table for interactions.
 *
 * <p>Not a Spring bean — instantiate directly or via factory.
 */
public class InteractionMapper {

    private final ObjectMapper objectMapper;

    public InteractionMapper() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Allows injecting a shared ObjectMapper (e.g. Spring's).
     */
    public InteractionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Maps a protocol interaction to a new ConfirmationRequestEntity for persistence.
     */
    public ConfirmationRequestEntity toEntity(WorkflowNodeInteraction interaction,
                                               String workItemId,
                                               String workflowInstanceId,
                                               String workflowNodeInstanceId,
                                               String agentSessionId,
                                               String runtimeType,
                                               String runtimeSessionId,
                                               String skillName) {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();

        entity.setRequestType(mapInteractionType(interaction));
        entity.setStatus(ConfirmationStatus.PENDING.name());

        // Context fields
        entity.setWorkItemId(workItemId);
        entity.setWorkflowInstanceId(workflowInstanceId);
        entity.setWorkflowNodeInstanceId(workflowNodeInstanceId);
        entity.setAgentSessionId(agentSessionId);
        entity.setRuntimeType(runtimeType);
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setSkillName(skillName);

        // Content fields
        entity.setTitle(interaction.getTitle());
        entity.setContent(interaction.getQuestion());
        entity.setContextSummary(interaction.getQuestion());

        // Options as JSON
        entity.setOptionsJson(serializeOptions(interaction));

        // Interaction protocol fields (V16 migration)
        entity.setInteractionId(interaction.getId());
        entity.setInteractionType(interaction.getType().name());
        entity.setInteractionSchemaJson(serializeInteractionSchema(interaction));
        entity.setInteractionRequired(interaction.isRequired() ? 1 : 0);

        // Priority defaults
        entity.setPriority("MEDIUM");

        return entity;
    }

    /**
     * Maps WorkflowNodeInteractionType to ConfirmationRequestType.
     * Maintains backward compatibility with existing confirmation types.
     */
    public String mapInteractionType(WorkflowNodeInteractionType type) {
        return switch (type) {
            case ASK_USER, INPUT -> ConfirmationRequestType.INPUT_REQUIRED.name();
            case DECISION -> ConfirmationRequestType.DECISION.name();
            case APPROVAL, ARTIFACT_REVIEW -> ConfirmationRequestType.APPROVAL.name();
            case PERMISSION -> ConfirmationRequestType.PERMISSION.name();
            case BLOCKER -> ConfirmationRequestType.EXCEPTION.name();
            case CUSTOM_FORM -> ConfirmationRequestType.INPUT_REQUIRED.name();
            case RANKING, SCALE -> ConfirmationRequestType.DECISION.name();
        };
    }

    public String mapInteractionType(WorkflowNodeInteraction interaction) {
        if (interaction == null) {
            return ConfirmationRequestType.INPUT_REQUIRED.name();
        }
        WorkflowNodeInteractionType type = interaction.getType();
        if (type == WorkflowNodeInteractionType.SCALE && interaction.getOptions().isEmpty()) {
            return ConfirmationRequestType.INPUT_REQUIRED.name();
        }
        return mapInteractionType(type);
    }

    String serializeOptions(WorkflowNodeInteraction interaction) {
        try {
            if (interaction.getOptions() == null || interaction.getOptions().isEmpty()) {
                return null;
            }
            List<Map<String, String>> optionMaps = interaction.getOptions().stream()
                    .map(this::optionMap)
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(optionMaps);
        } catch (Exception e) {
            return null;
        }
    }

    String serializeInteractionSchema(WorkflowNodeInteraction interaction) {
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("id", interaction.getId());
            schema.put("type", interaction.getType().name());
            schema.put("title", interaction.getTitle());
            schema.put("question", interaction.getQuestion());
            if (interaction.getSelection() != null) {
                schema.put("selection", interaction.getSelection());
            }

            if (interaction.getOptions() != null && !interaction.getOptions().isEmpty()) {
                List<Map<String, String>> optionList = interaction.getOptions().stream()
                        .map(this::optionMap)
                        .collect(Collectors.toList());
                schema.put("options", optionList);
            }

            if (interaction.getFields() != null && !interaction.getFields().isEmpty()) {
                List<Map<String, Object>> fieldList = interaction.getFields().stream()
                        .map(this::fieldMap)
                        .collect(Collectors.toList());
                schema.put("fields", fieldList);
            }

            schema.put("allowCustom", interaction.isAllowCustom());
            schema.put("required", interaction.isRequired());

            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> optionMap(WorkflowNodeInteraction.InteractionOption opt) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", opt.getId());
        m.put("label", opt.getLabel());
        if (opt.getDescription() != null) {
            m.put("description", opt.getDescription());
        }
        if (opt.getActionType() != null && !opt.getActionType().isBlank()) {
            m.put("actionType", opt.getActionType());
        }
        return m;
    }

    private Map<String, Object> fieldMap(WorkflowNodeInteraction.InteractionField field) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", field.getId());
        m.put("label", field.getLabel());
        m.put("type", field.getType());
        m.put("required", field.isRequired());
        if (field.isAllowCustom()) {
            m.put("allowCustom", true);
        }
        if (field.getPlaceholder() != null && !field.getPlaceholder().isBlank()) {
            m.put("placeholder", field.getPlaceholder());
        }
        if (!field.getOptions().isEmpty()) {
            List<Map<String, String>> options = field.getOptions().stream()
                    .map(option -> {
                        Map<String, String> optionMap = new LinkedHashMap<>();
                        optionMap.put("value", option.getValue());
                        optionMap.put("label", option.getLabel());
                        return optionMap;
                    })
                    .collect(Collectors.toList());
            m.put("options", options);
        }
        return m;
    }
}
