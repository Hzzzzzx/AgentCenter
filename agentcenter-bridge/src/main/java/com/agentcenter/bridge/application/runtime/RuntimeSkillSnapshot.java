package com.agentcenter.bridge.application.runtime;

import java.time.OffsetDateTime;
import java.util.List;

import com.agentcenter.bridge.api.dto.RuntimeSkillDto;

public record RuntimeSkillSnapshot(
        OffsetDateTime refreshedAt,
        String projectRoot,
        List<RuntimeSkillDto> skills
) {}
