package com.agentcenter.bridge.application;

import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeSkillEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeSkillVersionMapper;
import com.agentcenter.bridge.infrastructure.persistence.mapper.WorkflowMapper;
import com.agentcenter.bridge.application.runtime.RuntimeGateway;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillRegistryServiceTest {

    private RuntimeSkillMapper skillMapper;
    private SkillRegistryService service;

    @BeforeEach
    void setUp() {
        skillMapper = mock(RuntimeSkillMapper.class);
        service = new SkillRegistryService(
                skillMapper,
                mock(RuntimeSkillVersionMapper.class),
                mock(RuntimeResourceAuditService.class),
                mock(WorkflowMapper.class),
                mock(IdGenerator.class),
                mock(RuntimeGateway.class),
                mock(RuntimeResourceService.class),
                mock(ProjectRuntimeWorkspaceResolver.class)
        );
    }

    @Test
    void shouldRejectSkillWhenOnlyTypoDiffers() {
        RuntimeSkillEntity entity = runnableSkill("prd-design");
        when(skillMapper.findByProjectIdAndName("01DEFAULTPROJECT0000000000001", "prd-desingn")).thenReturn(null);
        when(skillMapper.findByProjectId("01DEFAULTPROJECT0000000000001")).thenReturn(List.of(entity));

        String error = service.validateRegisteredRunnableSkill("01DEFAULTPROJECT0000000000001", "prd-desingn");

        assertThat(error)
                .contains("Use the exact project Skill name")
                .contains("Did you mean: prd-design?");
    }

    @Test
    void shouldRejectSkillWhenSeparatorDiffers() {
        RuntimeSkillEntity entity = runnableSkill("hld_design");
        when(skillMapper.findByProjectIdAndName("01DEFAULTPROJECT0000000000001", "hld-design")).thenReturn(null);
        when(skillMapper.findByProjectId("01DEFAULTPROJECT0000000000001")).thenReturn(List.of(entity));

        String error = service.validateRegisteredRunnableSkill("01DEFAULTPROJECT0000000000001", "hld-design");

        assertThat(error)
                .contains("Use the exact project Skill name")
                .contains("Did you mean: hld_design?");
    }

    @Test
    void shouldValidateExactRunnableSkill() {
        RuntimeSkillEntity entity = runnableSkill("prd-design");
        when(skillMapper.findByProjectIdAndName("01DEFAULTPROJECT0000000000001", "prd-design")).thenReturn(entity);

        String error = service.validateRegisteredRunnableSkill("01DEFAULTPROJECT0000000000001", "prd-design");

        assertThat(error).isNull();
    }

    private RuntimeSkillEntity runnableSkill(String name) {
        RuntimeSkillEntity entity = new RuntimeSkillEntity();
        entity.setName(name);
        entity.setStatus("ENABLED");
        entity.setValidationStatus("VALID");
        return entity;
    }
}
