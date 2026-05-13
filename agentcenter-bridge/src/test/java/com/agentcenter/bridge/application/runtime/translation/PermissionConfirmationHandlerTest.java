package com.agentcenter.bridge.application.runtime.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.application.RuntimeEventService;
import com.agentcenter.bridge.application.confirmation.ConfirmationCreatedEventPayloadBuilder;
import com.agentcenter.bridge.domain.confirmation.ConfirmationRequestType;
import com.agentcenter.bridge.domain.confirmation.ConfirmationStatus;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.persistence.entity.ConfirmationRequestEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.ConfirmationMapper;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeRuntimeAdapter;

class PermissionConfirmationHandlerTest {

    private ConfirmationMapper confirmationMapper;
    private RuntimeEventService runtimeEventService;
    private OpenCodeRuntimeAdapter adapter;
    private ObjectProvider<OpenCodeRuntimeAdapter> adapterProvider;
    private ConfirmationCreatedEventPayloadBuilder confirmationCreatedEventPayloadBuilder;
    private PermissionConfirmationHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        confirmationMapper = mock(ConfirmationMapper.class);
        runtimeEventService = mock(RuntimeEventService.class);
        adapter = mock(OpenCodeRuntimeAdapter.class);
        adapterProvider = mock(ObjectProvider.class);
        confirmationCreatedEventPayloadBuilder = mock(ConfirmationCreatedEventPayloadBuilder.class);
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        when(confirmationCreatedEventPayloadBuilder.buildPayload(any(ConfirmationRequestEntity.class)))
                .thenAnswer(invocation -> {
                    ConfirmationRequestEntity entity = invocation.getArgument(0);
                    return "{\"confirmationId\":\"" + entity.getId() + "\"}";
                });
        handler = new PermissionConfirmationHandler(
                confirmationMapper, runtimeEventService, adapterProvider, confirmationCreatedEventPayloadBuilder);
    }

    @Test
    void createPermissionConfirmationAutoApprovesMatchingSessionAlwaysScope() {
        ConfirmationRequestEntity prior = resolvedPermission(
                "perm_ses_old_per_old",
                "agent-1",
                """
                {
                  "permission": "external_directory",
                  "patterns": "/workspace/protected/*",
                  "always": "/workspace/protected/*"
                }
                """,
                "{\"reply\":\"always\"}");
        when(confirmationMapper.findById("perm_ses_new_per_new")).thenReturn(null);
        when(confirmationMapper.findPermissionHistoryByAgentSessionId("agent-1"))
                .thenReturn(List.of(prior));

        handler.createPermissionConfirmation(
                "agent-1",
                "ses_new",
                "per_new",
                "OpenCode 请求访问外部目录",
                "external_directory",
                """
                {
                  "permission": "external_directory",
                  "patterns": "/workspace/protected/UploadSplitter.ts",
                  "always": "/workspace/protected/*"
                }
                """);

        verify(adapter).respondPermission("ses_new", "per_new", "always");

        ArgumentCaptor<ConfirmationRequestEntity> entityCaptor =
                ArgumentCaptor.forClass(ConfirmationRequestEntity.class);
        verify(confirmationMapper).insert(entityCaptor.capture());
        ConfirmationRequestEntity inserted = entityCaptor.getValue();
        assertEquals(ConfirmationStatus.RESOLVED.name(), inserted.getStatus());
        assertEquals("system", inserted.getResolvedBy());
        assertTrue(inserted.getResolutionPayloadJson().contains("\"autoApproved\":true"));
        assertTrue(inserted.getResolutionPayloadJson().contains("perm_ses_old_per_old"));

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        RuntimeEventDto event = eventCaptor.getValue();
        assertEquals(RuntimeEventType.CONFIRMATION_RESOLVED, event.eventType());
        assertEquals("agent-1", event.sessionId());
        assertTrue(event.payloadJson().contains("perm_ses_new_per_new"));
    }

    @Test
    void handlePermissionRepliedResolvesPendingConfirmation() {
        String confirmationId = PermissionConfirmationHandler.confirmationIdFor("ses_1", "per_1");
        ConfirmationRequestEntity pending = pendingPermission(confirmationId, "agent-1", "ses_1", "per_1");
        when(confirmationMapper.findById(confirmationId)).thenReturn(pending);

        handler.handlePermissionReplied("agent-1", "ses_1", "per_1", "always");

        assertEquals(ConfirmationStatus.RESOLVED.name(), pending.getStatus());
        assertEquals("opencode", pending.getResolvedBy());
        assertEquals("always", pending.getResolutionComment());
        assertTrue(pending.getResolutionPayloadJson().contains("opencode.permission.replied"));
        verify(confirmationMapper).update(pending);

        ArgumentCaptor<RuntimeEventDto> eventCaptor = ArgumentCaptor.forClass(RuntimeEventDto.class);
        verify(runtimeEventService).publishEvent(eventCaptor.capture());
        assertEquals(RuntimeEventType.CONFIRMATION_RESOLVED, eventCaptor.getValue().eventType());
    }

    @Test
    void handlePermissionRepliedRejectsPendingConfirmation() {
        String confirmationId = PermissionConfirmationHandler.confirmationIdFor("ses_1", "per_2");
        ConfirmationRequestEntity pending = pendingPermission(confirmationId, "agent-1", "ses_1", "per_2");
        when(confirmationMapper.findById(confirmationId)).thenReturn(pending);

        handler.handlePermissionReplied("agent-1", "ses_1", "per_2", "reject");

        assertEquals(ConfirmationStatus.REJECTED.name(), pending.getStatus());
        assertEquals("reject", pending.getResolutionComment());
        verify(confirmationMapper).update(pending);
    }

    private ConfirmationRequestEntity resolvedPermission(String id, String agentSessionId,
                                                          String contextJson, String payloadJson) {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId(id);
        entity.setRequestType(ConfirmationRequestType.PERMISSION.name());
        entity.setStatus(ConfirmationStatus.RESOLVED.name());
        entity.setAgentSessionId(agentSessionId);
        entity.setInteractionContextJson(contextJson);
        entity.setResolutionPayloadJson(payloadJson);
        return entity;
    }

    private ConfirmationRequestEntity pendingPermission(String id, String agentSessionId,
                                                         String runtimeSessionId, String permissionId) {
        ConfirmationRequestEntity entity = new ConfirmationRequestEntity();
        entity.setId(id);
        entity.setRequestType(ConfirmationRequestType.PERMISSION.name());
        entity.setStatus(ConfirmationStatus.PENDING.name());
        entity.setAgentSessionId(agentSessionId);
        entity.setRuntimeSessionId(runtimeSessionId);
        entity.setInteractionId(permissionId);
        entity.setTitle("OpenCode permission request");
        entity.setContent("OpenCode permission request");
        return entity;
    }
}
