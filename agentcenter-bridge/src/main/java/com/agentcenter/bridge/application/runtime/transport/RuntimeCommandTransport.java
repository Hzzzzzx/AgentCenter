package com.agentcenter.bridge.application.runtime.transport;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import java.time.Duration;

/**
 * SPI for sending a command to a runtime and receiving an ack synchronously.
 */
public interface RuntimeCommandTransport {

    /**
     * Send a command and block until an ack is received (default timeout).
     */
    RuntimeAckEnvelope send(RuntimeCommandEnvelope command);

    /**
     * Send a command and block until an ack is received or the timeout elapses.
     */
    RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout);
}
