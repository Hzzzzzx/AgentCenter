package com.agentcenter.bridge.application.runtime.translation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeRuntimeAdapter;

@Component
public class PermissionConfirmationHandler {
    private static final Logger log = LoggerFactory.getLogger(PermissionConfirmationHandler.class);
    private static final DateTimeFormatter SQLITE_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfirmationMapper confirmationMapper;
    private final RuntimeEventService runtimeEventService;
    private final ObjectProvider<OpenCodeRuntimeAdapter> runtimeAdapterProvider;

    public PermissionConfirmationHandler(ConfirmationMapper confirmationMapper,
                                          RuntimeEventService runtimeEventService,
                                          ObjectProvider<OpenCodeRuntimeAdapter> runtimeAdapterProvider) {
        this.confirmationMapper = confirmationMapper;
        this.runtimeEventService = runtimeEventService;
        this.runtimeAdapterProvider = runtimeAdapterProvider;
    }

    @Transactional
    public void createPermissionConfirmation(String agentSessionId, String opencodeSessionId,
                                              String permissionId, String title, String skillName,
                                              String interactionContextJson) {
        String confirmationId = confirmationIdFor(opencodeSessionId, permissionId);
        var existing = confirmationMapper.findById(confirmationId);
        if (existing != null) {
            log.info("Permission confirmation already exists for permissionId={}", permissionId);
            return;
        }

        String now = LocalDateTime.now().format(SQLITE_DT);
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId(confirmationId);
        entity.setRequestType(ConfirmationRequestType.PERMISSION.name());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setAgentSessionId(agentSessionId);
        entity.setRuntimeType(RuntimeType.OPENCODE.name());
        entity.setRuntimeSessionId(opencodeSessionId);
        entity.setInteractionId(permissionId);
        entity.setSkillName(skillName);
        entity.setTitle(title);
        entity.setContent("OpenCode permission request");
        entity.setInteractionContextJson(interactionContextJson);
        entity.setOptionsJson("[{\"value\":\"once\",\"label\":\"允许一次\"},"
                + "{\"value\":\"always\",\"label\":\"始终允许\"},"
                + "{\"value\":\"reject\",\"label\":\"拒绝\"}]");
        entity.setPriority(Priority.HIGH.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        confirmationMapper.insert(entity);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, agentSessionId, null, null, null,
                RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                "{\"confirmationId\":\"" + entity.getId() + "\"}", null
        ));
        log.info("Created PERMISSION confirmation id={} for agentSession={}", entity.getId(), agentSessionId);
    }

    public void respondPermission(String opencodeSessionId, String permissionId, boolean approved) {
        respondPermission(opencodeSessionId, permissionId, approved ? "once" : "reject");
    }

    public void respondPermission(String opencodeSessionId, String permissionId, String reply) {
        OpenCodeRuntimeAdapter adapter = runtimeAdapterProvider.getIfAvailable();
        if (adapter == null) {
            throw new IllegalStateException("OpenCodeRuntimeAdapter not available for permissionId=" + permissionId);
        }
        adapter.respondPermission(opencodeSessionId, permissionId, normalizeReply(reply));
        log.info("Sent permission.respond for permissionId={}, reply={}", permissionId, reply);
    }

    public static String confirmationIdFor(String opencodeSessionId, String permissionId) {
        String sessionPart = normalizeIdPart(opencodeSessionId, "session");
        String permissionPart = normalizeIdPart(permissionId, "permission");
        return "perm_" + sessionPart + "_" + permissionPart;
    }

    private static String normalizeIdPart(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String normalizeReply(String reply) {
        if ("always".equals(reply) || "reject".equals(reply)) return reply;
        return "once";
    }
}
