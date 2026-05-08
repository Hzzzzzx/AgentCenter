package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeHttpCommandTransport;
import com.agentcenter.bridge.infrastructure.runtime.opencode.transport.OpenCodeSseEventStreamTransport;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class OpenCodeTransportConfig {

    @Bean
    public OpenCodeHttpCommandTransport openCodeHttpCommandTransport(ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new OpenCodeHttpCommandTransport(httpClient, objectMapper);
    }

    @Bean
    public OpenCodeSseEventStreamTransport openCodeSseEventStreamTransport(ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new OpenCodeSseEventStreamTransport(httpClient, objectMapper);
    }
}
