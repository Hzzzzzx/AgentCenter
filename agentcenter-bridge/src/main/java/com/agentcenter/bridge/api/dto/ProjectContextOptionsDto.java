package com.agentcenter.bridge.api.dto;

import java.util.List;

public record ProjectContextOptionsDto(
        List<String> cloudeReqProjects,
        List<String> spaces,
        List<String> iterations
) {}
