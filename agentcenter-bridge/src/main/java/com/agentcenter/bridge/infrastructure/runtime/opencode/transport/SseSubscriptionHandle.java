package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.agentcenter.bridge.application.runtime.transport.SubscriptionHandle;

/**
 * Thread-safe {@link SubscriptionHandle} for an SSE subscription.
 * Wraps a close action and tracks active state via {@link AtomicBoolean}.
 */
public class SseSubscriptionHandle implements SubscriptionHandle {

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Consumer<SseSubscriptionHandle> onClose;

    /**
     * @param onClose callback invoked on first {@link #close()} — may be null.
     */
    public SseSubscriptionHandle(Consumer<SseSubscriptionHandle> onClose) {
        this.onClose = onClose;
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false) && onClose != null) {
            onClose.accept(this);
        }
    }
}
