package com.agentcenter.bridge.application.runtime;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * Selects a RuntimeProvider by RuntimeType.
 */
public interface RuntimeProviderRegistry {
    RuntimeProvider getProvider(RuntimeType type);
}
