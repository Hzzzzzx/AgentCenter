package com.agentcenter.bridge.infrastructure.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UlidIdGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
