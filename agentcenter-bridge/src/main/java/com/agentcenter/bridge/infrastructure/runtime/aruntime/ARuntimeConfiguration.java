package com.agentcenter.bridge.infrastructure.runtime.aruntime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agentcenter.bridge.application.runtime.RuntimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ARuntimeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agentcenter.runtime.a", name = "enabled", havingValue = "true")
    RuntimeProvider aRuntimeProvider(ObjectMapper objectMapper) {
        return new ARuntimeProvider(new ARuntimeFakeTransport(), objectMapper);
    }
}
