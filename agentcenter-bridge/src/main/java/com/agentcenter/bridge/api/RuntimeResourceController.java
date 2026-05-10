package com.agentcenter.bridge.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentcenter.bridge.api.dto.RuntimeEnvironmentStatusDto;
import com.agentcenter.bridge.api.dto.RuntimeSkillRefreshResponse;
import com.agentcenter.bridge.application.RuntimeEnvironmentStatusService;
import com.agentcenter.bridge.application.RuntimeResourceService;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeResourceController {

    private final RuntimeEnvironmentStatusService runtimeEnvironmentStatusService;
    private final RuntimeResourceService runtimeResourceService;

    public RuntimeResourceController(RuntimeEnvironmentStatusService runtimeEnvironmentStatusService,
                                     RuntimeResourceService runtimeResourceService) {
        this.runtimeEnvironmentStatusService = runtimeEnvironmentStatusService;
        this.runtimeResourceService = runtimeResourceService;
    }

    @GetMapping("/status")
    public RuntimeEnvironmentStatusDto status() {
        return runtimeEnvironmentStatusService.currentStatus();
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
