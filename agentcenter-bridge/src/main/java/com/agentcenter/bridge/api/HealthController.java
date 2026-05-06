package com.agentcenter.bridge.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final String runtimeType;

    public HealthController(@Value("${agentcenter.runtime.default-type:OPENCODE}") String runtimeType) {
        this.runtimeType = runtimeType;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "runtime", runtimeType.toLowerCase());
    }
}
