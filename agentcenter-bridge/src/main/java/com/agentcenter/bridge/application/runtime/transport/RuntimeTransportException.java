package com.agentcenter.bridge.application.runtime.transport;

/**
 * RuntimeException subclass for transport-level errors.
 * Carries a recoverable flag indicating whether retry is appropriate.
 */
public class RuntimeTransportException extends RuntimeException {

    private final boolean recoverable;

    public RuntimeTransportException(String message) {
        super(message);
        this.recoverable = false;
    }

    public RuntimeTransportException(String message, Throwable cause) {
        super(message, cause);
        this.recoverable = false;
    }

    public RuntimeTransportException(String message, Throwable cause, boolean recoverable) {
        super(message, cause);
        this.recoverable = recoverable;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
