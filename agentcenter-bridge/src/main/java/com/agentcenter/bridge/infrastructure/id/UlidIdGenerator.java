package com.agentcenter.bridge.infrastructure.id;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.stereotype.Component;

@Component
public class UlidIdGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UlidCreator.getUlid().toString();
    }
}
