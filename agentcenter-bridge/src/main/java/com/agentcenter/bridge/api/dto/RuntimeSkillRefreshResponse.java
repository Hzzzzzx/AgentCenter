package com.agentcenter.bridge.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RuntimeSkillRefreshResponse(
        OffsetDateTime refreshedAt,
        String projectRoot,
        String skillsPath,
        int skillCount,
        List<RuntimeSkillDto> skills
) {}
