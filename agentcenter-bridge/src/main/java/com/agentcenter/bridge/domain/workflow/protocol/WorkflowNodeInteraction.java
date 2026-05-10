package com.agentcenter.bridge.domain.workflow.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single interaction element within a workflow node state.
 * Represents one question/prompt the Agent sends to the user.
 */
public class WorkflowNodeInteraction {

    private String id;
    private WorkflowNodeInteractionType type;
    private String title;
    private String question;
    private String selection;
    private List<InteractionOption> options;
    private List<InteractionField> fields;
    private boolean allowCustom;
    private boolean required = true;

    public WorkflowNodeInteraction() {
        this.options = new ArrayList<>();
        this.fields = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public WorkflowNodeInteractionType getType() {
        return type;
    }

    public void setType(WorkflowNodeInteractionType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    public List<InteractionOption> getOptions() {
        return options != null ? Collections.unmodifiableList(options) : Collections.emptyList();
    }

    public void setOptions(List<InteractionOption> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public List<InteractionField> getFields() {
        return fields != null ? Collections.unmodifiableList(fields) : Collections.emptyList();
    }

    public void setFields(List<InteractionField> fields) {
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    }

    public boolean isAllowCustom() {
        return allowCustom;
    }

    public void setAllowCustom(boolean allowCustom) {
        this.allowCustom = allowCustom;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * A selectable option within a DECISION-type interaction.
     */
    public static class InteractionOption {
        private String id;
        private String label;
        private String description;

        public InteractionOption() {}

        public InteractionOption(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InteractionOption that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(label, that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label);
        }
    }

    /**
     * A form field within an INPUT/CUSTOM_FORM-type interaction.
     */
    public static class InteractionField {
        private String id;
        private String label;
        private String type;
        private boolean required = true;

        public InteractionField() {}

        public InteractionField(String id, String label, String type, boolean required) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.required = required;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InteractionField that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(label, that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label);
        }
    }
}
