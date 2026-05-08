package com.agentcenter.bridge.application.runtime.transport;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeRawEvent;

/**
 * SPI for subscribing to a runtime event stream.
 */
public interface RuntimeEventStreamTransport {

    /**
     * Subscribe to the event stream, delivering events to the provided sink.
     * Returns a handle for lifecycle control.
     */
    SubscriptionHandle subscribe(RuntimeEventSink sink);
}
