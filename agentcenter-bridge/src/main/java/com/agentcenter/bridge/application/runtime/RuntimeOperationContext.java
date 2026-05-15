package com.agentcenter.bridge.application.runtime;

/**
 * Bridge-owned runtime context carried across providers and transports.
 * Keeps AgentCenter identifiers stable while each Runtime maps them to its own protocol.
 */
public record RuntimeOperationContext(
        String projectId,
        String operationId,
        String idempotencyKey,
        String messageId,
        String correlationId,
        String agentSessionId,
        String runtimeSessionId,
        String workItemId,
        String workflowInstanceId,
        String workflowNodeInstanceId,
        String createdBy
) {
    public static RuntimeOperationContext empty() {
        return new RuntimeOperationContext(null, null, null, null, null,
                null, null, null, null, null, "system");
    }

    public static RuntimeOperationContext forSession(String workItemId, String agentSessionId, String runtimeSessionId) {
        return empty()
                .withWorkItemId(workItemId)
                .withAgentSessionId(agentSessionId)
                .withRuntimeSessionId(runtimeSessionId);
    }

    public RuntimeOperationContext withProjectId(String value) {
        return copy(value, operationId, idempotencyKey, messageId, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withOperationId(String value) {
        return copy(projectId, value, idempotencyKey, messageId, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withIdempotencyKey(String value) {
        return copy(projectId, operationId, value, messageId, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withMessageId(String value) {
        return copy(projectId, operationId, idempotencyKey, value, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withCorrelationId(String value) {
        return copy(projectId, operationId, idempotencyKey, messageId, value, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withAgentSessionId(String value) {
        return copy(projectId, operationId, idempotencyKey, messageId, correlationId, value,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withRuntimeSessionId(String value) {
        return copy(projectId, operationId, idempotencyKey, messageId, correlationId, agentSessionId,
                value, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withWorkItemId(String value) {
        return copy(projectId, operationId, idempotencyKey, messageId, correlationId, agentSessionId,
                runtimeSessionId, value, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withWorkflowContext(String workflowInstanceId, String workflowNodeInstanceId) {
        return copy(projectId, operationId, idempotencyKey, messageId, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }

    public RuntimeOperationContext withCreatedBy(String value) {
        return copy(projectId, operationId, idempotencyKey, messageId, correlationId, agentSessionId,
                runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, value);
    }

    public String createdByOrSystem() {
        return createdBy == null || createdBy.isBlank() ? "system" : createdBy;
    }

    private RuntimeOperationContext copy(String projectId,
                                          String operationId,
                                          String idempotencyKey,
                                          String messageId,
                                          String correlationId,
                                          String agentSessionId,
                                          String runtimeSessionId,
                                          String workItemId,
                                          String workflowInstanceId,
                                          String workflowNodeInstanceId,
                                          String createdBy) {
        return new RuntimeOperationContext(projectId, operationId, idempotencyKey, messageId, correlationId,
                agentSessionId, runtimeSessionId, workItemId, workflowInstanceId, workflowNodeInstanceId, createdBy);
    }
}
