package com.agentcenter.bridge.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.application.RuntimeResourceService;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeResourceController {

    private final RuntimeResourceService runtimeResourceService;

    public RuntimeResourceController(RuntimeResourceService runtimeResourceService) {
        this.runtimeResourceService = runtimeResourceService;
    }

    @GetMapping("/skills")
    public RuntimeSkillRefreshResponse listSkills() {
        return runtimeResourceService.listSkills();
    }

    @PostMapping("/skills/refresh")
    public RuntimeSkillRefreshResponse refreshSkills() {
        return runtimeResourceService.refreshSkills();
    }
}
