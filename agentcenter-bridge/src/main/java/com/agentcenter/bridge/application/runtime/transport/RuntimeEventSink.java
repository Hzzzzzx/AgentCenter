package com.agentcenter.bridge.application.runtime.transport;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;

/**
 * Callback interface for receiving events from the transport layer.
 */
public interface RuntimeEventSink {

    /**
     * Called when a raw event is received from the runtime.
     */
    void onEvent(RuntimeRawEvent event);

    /**
     * Called when a transport-level error occurs.
     */
    void onError(RuntimeTransportException error);

    /**
     * Called when the event stream is closed by the transport.
     */
    void onClose();
}
