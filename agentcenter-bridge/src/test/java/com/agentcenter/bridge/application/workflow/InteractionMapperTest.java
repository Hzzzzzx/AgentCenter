package com.agentcenter.bridge.application.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteraction;
import com.agentcenter.bridge.domain.workflow.protocol.WorkflowNodeInteractionType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class InteractionMapperTest {

    private InteractionMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new InteractionMapper(objectMapper);
    }

    private WorkflowNodeInteraction buildInteraction(WorkflowNodeInteractionType type) {
        WorkflowNodeInteraction interaction = new WorkflowNodeInteraction();
        interaction.setId("UIP-001");
        interaction.setType(type);
        interaction.setTitle("选择方案");
        interaction.setQuestion("请选择推进方案");
        interaction.setRequired(true);
        return interaction;
    }

    private WorkflowNodeInteraction buildDecisionInteraction() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.DECISION);
        interaction.setSelection("single");
        interaction.setAllowCustom(true);
        interaction.setOptions(List.of(
                new WorkflowNodeInteraction.InteractionOption("A", "方案A", "快速实施"),
                new WorkflowNodeInteraction.InteractionOption("B", "方案B", "稳妥实施")
        ));
        return interaction;
    }

    @Test
    void toEntity_decisionInteraction() {
        WorkflowNodeInteraction interaction = buildDecisionInteraction();

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(ConfirmationRequestType.DECISION.name(), entity.getRequestType());
        assertEquals("选择方案", entity.getTitle());
        assertEquals("请选择推进方案", entity.getContent());
    }

    @Test
    void toEntity_inputInteraction() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.INPUT);
        interaction.setFields(List.of(
                new WorkflowNodeInteraction.InteractionField("f1", "姓名", "text", true),
                new WorkflowNodeInteraction.InteractionField("f2", "邮箱", "email", false)
        ));

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(ConfirmationRequestType.INPUT_REQUIRED.name(), entity.getRequestType());
    }

    @Test
    void toEntity_approvalInteraction() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.APPROVAL);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(ConfirmationRequestType.APPROVAL.name(), entity.getRequestType());
    }

    @Test
    void toEntity_permissionInteraction() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.PERMISSION);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(ConfirmationRequestType.PERMISSION.name(), entity.getRequestType());
    }

    @Test
    void toEntity_blockerInteraction() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.BLOCKER);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(ConfirmationRequestType.EXCEPTION.name(), entity.getRequestType());
    }

    @Test
    void toEntity_setsContextFields() {
        WorkflowNodeInteraction interaction = buildDecisionInteraction();

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-100", "WFI-200", "WNI-300",
                "SES-400", "OPENCODE", "RT-SES-500", "code-review"
        );

        assertEquals("WI-100", entity.getWorkItemId());
        assertEquals("WFI-200", entity.getWorkflowInstanceId());
        assertEquals("WNI-300", entity.getWorkflowNodeInstanceId());
        assertEquals("SES-400", entity.getAgentSessionId());
        assertEquals("OPENCODE", entity.getRuntimeType());
        assertEquals("RT-SES-500", entity.getRuntimeSessionId());
        assertEquals("code-review", entity.getSkillName());
        assertEquals(ConfirmationStatus.PENDING.name(), entity.getStatus());
        assertEquals("MEDIUM", entity.getPriority());
    }

    @Test
    void toEntity_setsInteractionFields() throws Exception {
        WorkflowNodeInteraction interaction = buildDecisionInteraction();

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals("UIP-001", entity.getInteractionId());
        assertEquals("DECISION", entity.getInteractionType());
        assertEquals(1, entity.getInteractionRequired());

        assertNotNull(entity.getInteractionSchemaJson());
        JsonNode schema = objectMapper.readTree(entity.getInteractionSchemaJson());
        assertEquals("UIP-001", schema.get("id").asText());
        assertEquals("DECISION", schema.get("type").asText());
        assertEquals("选择方案", schema.get("title").asText());
        assertTrue(schema.get("required").asBoolean());
    }

    @Test
    void toEntity_optionsJson_hasStructuredOptions() throws Exception {
        WorkflowNodeInteraction interaction = buildDecisionInteraction();

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertNotNull(entity.getOptionsJson());
        JsonNode options = objectMapper.readTree(entity.getOptionsJson());
        assertTrue(options.isArray());
        assertEquals(2, options.size());

        JsonNode optionA = options.get(0);
        assertEquals("A", optionA.get("id").asText());
        assertEquals("方案A", optionA.get("label").asText());
        assertEquals("快速实施", optionA.get("description").asText());

        JsonNode optionB = options.get(1);
        assertEquals("B", optionB.get("id").asText());
        assertEquals("方案B", optionB.get("label").asText());
    }

    @ParameterizedTest
    @EnumSource(WorkflowNodeInteractionType.class)
    void mapInteractionType_allTypes(WorkflowNodeInteractionType type) {
        String mapped = mapper.mapInteractionType(type);

        assertNotNull(mapped, "Type " + type + " must not map to null");

        List<String> validTypes = Arrays.stream(ConfirmationRequestType.values())
                .map(ConfirmationRequestType::name)
                .toList();
        assertTrue(validTypes.contains(mapped),
                "Type " + type + " mapped to '" + mapped + "' which is not a valid ConfirmationRequestType");
    }

    @Test
    void toEntity_withFields() throws Exception {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.CUSTOM_FORM);
        interaction.setFields(List.of(
                new WorkflowNodeInteraction.InteractionField("name", "姓名", "text", true),
                new WorkflowNodeInteraction.InteractionField("email", "邮箱", "email", false)
        ));

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertNotNull(entity.getInteractionSchemaJson());
        JsonNode schema = objectMapper.readTree(entity.getInteractionSchemaJson());
        assertTrue(schema.has("fields"));
        JsonNode fields = schema.get("fields");
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).get("id").asText());
        assertEquals("姓名", fields.get(0).get("label").asText());
        assertEquals("text", fields.get(0).get("type").asText());
        assertTrue(fields.get(0).get("required").asBoolean());
        assertFalse(fields.get(1).get("required").asBoolean());
    }

    @Test
    void toEntity_requiredFalse_setsInteractionRequiredToZero() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.ASK_USER);
        interaction.setRequired(false);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertEquals(0, entity.getInteractionRequired());
    }

    @Test
    void toEntity_noOptions_optionsJsonIsNull() {
        WorkflowNodeInteraction interaction = buildInteraction(WorkflowNodeInteractionType.ASK_USER);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        assertNull(entity.getOptionsJson());
    }

    @Test
    void toEntity_interactionSchemaContainsAllowCustom() throws Exception {
        WorkflowNodeInteraction interaction = buildDecisionInteraction();
        interaction.setAllowCustom(true);

        ConfirmationRequestEntity entity = mapper.toEntity(
                interaction, "WI-001", "WFI-001", "WNI-001",
                "SES-001", "OPENCODE", "RT-SES-001", "planner"
        );

        JsonNode schema = objectMapper.readTree(entity.getInteractionSchemaJson());
        assertTrue(schema.get("allowCustom").asBoolean());
    }

    @Test
    void mapInteractionType_askUser_mapsToInputRequired() {
        assertEquals(
                ConfirmationRequestType.INPUT_REQUIRED.name(),
                mapper.mapInteractionType(WorkflowNodeInteractionType.ASK_USER)
        );
    }

    @Test
    void mapInteractionType_artifactReview_mapsToApproval() {
        assertEquals(
                ConfirmationRequestType.APPROVAL.name(),
                mapper.mapInteractionType(WorkflowNodeInteractionType.ARTIFACT_REVIEW)
        );
    }

    @Test
    void mapInteractionType_ranking_mapsToInputRequired() {
        assertEquals(
                ConfirmationRequestType.INPUT_REQUIRED.name(),
                mapper.mapInteractionType(WorkflowNodeInteractionType.RANKING)
        );
    }

    @Test
    void mapInteractionType_scale_mapsToInputRequired() {
        assertEquals(
                ConfirmationRequestType.INPUT_REQUIRED.name(),
                mapper.mapInteractionType(WorkflowNodeInteractionType.SCALE)
        );
    }
}
