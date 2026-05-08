package com.agentcenter.bridge.domain.runtime;

public enum RuntimeOperationStatus {
    CREATED,
    DISPATCHING,
    ACCEPTED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == TIMED_OUT;
    }
}
