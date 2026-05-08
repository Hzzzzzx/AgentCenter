package com.agentcenter.bridge.application.runtime;

import java.util.EnumMap;
import java.util.List;

import org.springframework.stereotype.Component;

import com.agentcenter.bridge.domain.runtime.RuntimeType;

/**
 * Collects all RuntimeProvider beans via Spring and indexes by RuntimeType.
 */
@Component
public class DefaultRuntimeProviderRegistry implements RuntimeProviderRegistry {

    private final EnumMap<RuntimeType, RuntimeProvider> providers;

    public DefaultRuntimeProviderRegistry(List<RuntimeProvider> providerList) {
        this.providers = new EnumMap<>(RuntimeType.class);
        for (RuntimeProvider provider : providerList) {
            RuntimeType type = provider.runtimeType();
            if (providers.containsKey(type)) {
                throw new IllegalStateException("Duplicate RuntimeProvider for type: " + type);
            }
            providers.put(type, provider);
        }
    }

    @Override
    public RuntimeProvider getProvider(RuntimeType type) {
        RuntimeProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No RuntimeProvider registered for type: " + type);
        }
        return provider;
    }
}
