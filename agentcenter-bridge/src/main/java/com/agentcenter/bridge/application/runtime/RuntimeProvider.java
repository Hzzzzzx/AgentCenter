package com.agentcenter.bridge.application.runtime;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * Aggregate interface for a single runtime provider.
 * Combines conversation, resource, and capability ports.
 */
public interface RuntimeProvider extends ConversationRuntimePort, RuntimeResourcePort, RuntimeCapabilityProvider {
    RuntimeType runtimeType();
}
