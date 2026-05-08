package com.agentcenter.bridge.application.runtime.transport;

/**
 * Handle returned by event stream subscription.
 * Supports close and status queries.
 */
public interface SubscriptionHandle extends AutoCloseable {

    /**
     * Returns true if the subscription is actively receiving events.
     */
    boolean isActive();

    /**
     * Close the subscription. After calling this, isActive() returns false.
     */
    @Override
    void close();
}
