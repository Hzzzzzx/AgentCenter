package com.agentcenter.bridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.agentcenter.bridge.api.websocket.SessionWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionWebSocketHandler sessionWebSocketHandler;

    public WebSocketConfig(SessionWebSocketHandler sessionWebSocketHandler) {
        this.sessionWebSocketHandler = sessionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionWebSocketHandler, "/ws/agent-sessions/{id}")
                .setAllowedOriginPatterns("*");
    }
}
