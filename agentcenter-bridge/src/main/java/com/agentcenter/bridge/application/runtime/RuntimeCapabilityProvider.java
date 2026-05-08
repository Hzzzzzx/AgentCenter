package com.agentcenter.bridge.application.runtime;

/**
 * Provides runtime descriptor and capability information.
 */
public interface RuntimeCapabilityProvider {
    RuntimeDescriptor descriptor();
    RuntimeCapabilities capabilities();
}
