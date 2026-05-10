package com.agentcenter.bridge.application.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable context object holding all data needed to compose a workflow node prompt.
 * Acts as a data-only transfer object between the caller and the composer.
 */
public class NodeExecutionContext {

    private final WorkItemData workItem;
    private final NodeData node;
    private final UpstreamArtifactData upstreamArtifact;
    private final List<ResolvedInteraction> resolvedInteractions;
    private final String supplementalInput;

    public NodeExecutionContext(WorkItemData workItem, NodeData node,
                                UpstreamArtifactData upstreamArtifact,
                                List<ResolvedInteraction> resolvedInteractions,
                                String supplementalInput) {
        this.workItem = workItem;
        this.node = node;
        this.upstreamArtifact = upstreamArtifact;
        this.resolvedInteractions = resolvedInteractions != null
                ? Collections.unmodifiableList(new ArrayList<>(resolvedInteractions))
                : Collections.emptyList();
        this.supplementalInput = supplementalInput;
    }

    public WorkItemData getWorkItem() { return workItem; }
    public NodeData getNode() { return node; }
    public UpstreamArtifactData getUpstreamArtifact() { return upstreamArtifact; }
    public List<ResolvedInteraction> getResolvedInteractions() { return resolvedInteractions; }
    public String getSupplementalInput() { return supplementalInput; }

    public static class WorkItemData {
        private final String id;
        private final String code;
        private final String title;
        private final String type;
        private final String status;
        private final String priority;
        private final String description;
        private final String projectId;
        private final String spaceId;
        private final String iterationId;
        private final String assigneeUserId;

        public WorkItemData(String id, String code, String title, String type, String status,
                            String priority, String description, String projectId, String spaceId,
                            String iterationId, String assigneeUserId) {
            this.id = id;
            this.code = code;
            this.title = title;
            this.type = type;
            this.status = status;
            this.priority = priority;
            this.description = description;
            this.projectId = projectId;
            this.spaceId = spaceId;
            this.iterationId = iterationId;
            this.assigneeUserId = assigneeUserId;
        }

        public String getId() { return id; }
        public String getCode() { return code; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getDescription() { return description; }
        public String getProjectId() { return projectId; }
        public String getSpaceId() { return spaceId; }
        public String getIterationId() { return iterationId; }
        public String getAssigneeUserId() { return assigneeUserId; }
    }

    public static class NodeData {
        private final String nodeId;
        private final String nodeName;
        private final String nodeKey;
        private final String stageKey;
        private final String skillName;
        private final String inputPolicy;
        private final String outputType;
        private final boolean requiredConfirmation;
        private final String stageGoal;

        public NodeData(String nodeId, String nodeName, String nodeKey, String stageKey,
                        String skillName, String inputPolicy, String outputType,
                        boolean requiredConfirmation, String stageGoal) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.nodeKey = nodeKey;
            this.stageKey = stageKey;
            this.skillName = skillName;
            this.inputPolicy = inputPolicy;
            this.outputType = outputType;
            this.requiredConfirmation = requiredConfirmation;
            this.stageGoal = stageGoal;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeName() { return nodeName; }
        public String getNodeKey() { return nodeKey; }
        public String getStageKey() { return stageKey; }
        public String getSkillName() { return skillName; }
        public String getInputPolicy() { return inputPolicy; }
        public String getOutputType() { return outputType; }
        public boolean isRequiredConfirmation() { return requiredConfirmation; }
        public String getStageGoal() { return stageGoal; }
    }

    public static class UpstreamArtifactData {
        private final String artifactId;
        private final String title;
        private final String content;
        private final String artifactType;
        private final String sourceNodeInstanceId;

        public UpstreamArtifactData(String artifactId, String title, String content,
                                    String artifactType, String sourceNodeInstanceId) {
            this.artifactId = artifactId;
            this.title = title;
            this.content = content;
            this.artifactType = artifactType;
            this.sourceNodeInstanceId = sourceNodeInstanceId;
        }

        public String getArtifactId() { return artifactId; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getArtifactType() { return artifactType; }
        public String getSourceNodeInstanceId() { return sourceNodeInstanceId; }
    }

    public static class ResolvedInteraction {
        private final String id;
        private final String title;
        private final String question;
        private final String type;
        private final String resolutionPayload;
        private final String comment;

        public ResolvedInteraction(String id, String title, String question, String type,
                                   String resolutionPayload, String comment) {
            this.id = id;
            this.title = title;
            this.question = question;
            this.type = type;
            this.resolutionPayload = resolutionPayload;
            this.comment = comment;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getQuestion() { return question; }
        public String getType() { return type; }
        public String getResolutionPayload() { return resolutionPayload; }
        public String getComment() { return comment; }
    }
}
