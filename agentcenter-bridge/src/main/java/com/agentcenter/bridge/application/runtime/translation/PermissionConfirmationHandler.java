package com.agentcenter.bridge.application.runtime.translation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PermissionConfirmationHandler {
    private static final Logger log = LoggerFactory.getLogger(PermissionConfirmationHandler.class);
    private static final DateTimeFormatter SQLITE_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PERMISSION_OPTIONS_JSON = "[{\"value\":\"once\",\"label\":\"允许一次\"},"
            + "{\"value\":\"always\",\"label\":\"始终允许\"},"
            + "{\"value\":\"reject\",\"label\":\"拒绝\"}]";

    private final ConfirmationMapper confirmationMapper;
    private final RuntimeEventService runtimeEventService;
    private final ObjectProvider<OpenCodeRuntimeAdapter> runtimeAdapterProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        ConfirmationRequestEntity entity = buildPermissionEntity(
                confirmationId, agentSessionId, opencodeSessionId, permissionId,
                title, skillName, interactionContextJson, now);

        PermissionContext context = parsePermissionContext(interactionContextJson);
        SessionPermissionApproval reusableApproval = findReusableApproval(agentSessionId, context);
        if (reusableApproval != null && respondPermissionSafely(opencodeSessionId, permissionId, "always")) {
            entity.setStatus(ConfirmationStatus.RESOLVED.name());
            entity.setResolvedBy("system");
            entity.setResolvedAt(now);
            entity.setResolutionComment("Auto-approved by prior session permission");
            entity.setResolutionPayloadJson(resolutionPayload("always",
                    "agentcenter.session_permission", true, reusableApproval.confirmationId()));
            confirmationMapper.insert(entity);
            publishPermissionResolvedEvent(entity, "APPROVE",
                    "本次会话已允许同类权限，自动通过：" + reusableApproval.confirmationId());
            log.info("Auto-approved permission confirmation id={} using prior always approval={}",
                    entity.getId(), reusableApproval.confirmationId());
            return;
        }

        confirmationMapper.insert(entity);

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null, agentSessionId, null, null, null,
                RuntimeEventType.CONFIRMATION_CREATED, RuntimeEventSource.BRIDGE,
                "{\"confirmationId\":\"" + entity.getId() + "\"}", null
        ));
        log.info("Created PERMISSION confirmation id={} for agentSession={}", entity.getId(), agentSessionId);
    }

    @Transactional
    public void handlePermissionReplied(String agentSessionId, String opencodeSessionId,
                                        String permissionId, String reply) {
        if (permissionId == null || permissionId.isBlank()) {
            return;
        }
        ConfirmationRequestEntity entity = confirmationMapper.findById(
                confirmationIdFor(opencodeSessionId, permissionId));
        if (entity == null) {
            log.info("Permission replied for unknown permissionId={} session={}", permissionId, opencodeSessionId);
            return;
        }
        if (!ConfirmationStatus.PENDING.name().equals(entity.getStatus())
                && !ConfirmationStatus.IN_CONVERSATION.name().equals(entity.getStatus())) {
            return;
        }

        String normalizedReply = normalizeReply(reply);
        String now = LocalDateTime.now().format(SQLITE_DT);
        boolean rejected = "reject".equals(normalizedReply);
        entity.setStatus(rejected ? ConfirmationStatus.REJECTED.name() : ConfirmationStatus.RESOLVED.name());
        entity.setResolvedBy("opencode");
        entity.setResolvedAt(now);
        entity.setResolutionComment(normalizedReply);
        entity.setResolutionPayloadJson(resolutionPayload(normalizedReply,
                "opencode.permission.replied", false, null));
        entity.setUpdatedAt(now);
        confirmationMapper.update(entity);
        publishPermissionResolvedEvent(entity, rejected ? "REJECT" : "APPROVE",
                "OpenCode 权限请求已处理：" + normalizedReply);
        log.info("Synchronized OpenCode permission reply confirmation={} reply={} agentSession={}",
                entity.getId(), normalizedReply, agentSessionId);
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

    private ConfirmationRequestEntity buildPermissionEntity(String confirmationId, String agentSessionId,
                                                            String opencodeSessionId, String permissionId,
                                                            String title, String skillName,
                                                            String interactionContextJson,
                                                            String now) {
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
        entity.setOptionsJson(PERMISSION_OPTIONS_JSON);
        entity.setPriority(Priority.HIGH.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private SessionPermissionApproval findReusableApproval(String agentSessionId, PermissionContext current) {
        if (agentSessionId == null || agentSessionId.isBlank()
                || current.permission().isBlank() || current.requestPatterns().isEmpty()) {
            return null;
        }

        List<ConfirmationRequestEntity> history =
                confirmationMapper.findPermissionHistoryByAgentSessionId(agentSessionId);
        if (history == null) {
            return null;
        }
        for (ConfirmationRequestEntity candidate : history) {
            if (!ConfirmationStatus.RESOLVED.name().equals(candidate.getStatus())) continue;
            if (!isAlwaysResolution(candidate.getResolutionPayloadJson())) continue;

            PermissionContext approvedContext = parsePermissionContext(candidate.getInteractionContextJson());
            if (!current.permission().equals(approvedContext.permission())) continue;
            List<String> approvedPatterns = approvedContext.approvedPatterns();
            if (approvedPatterns.isEmpty()) continue;

            boolean coversCurrentRequest = current.requestPatterns().stream()
                    .allMatch(pattern -> approvedPatterns.stream()
                            .anyMatch(approved -> wildcardMatches(approved, pattern)));
            if (coversCurrentRequest) {
                return new SessionPermissionApproval(candidate.getId());
            }
        }
        return null;
    }

    private boolean respondPermissionSafely(String opencodeSessionId, String permissionId, String reply) {
        try {
            respondPermission(opencodeSessionId, permissionId, reply);
            return true;
        } catch (Exception e) {
            log.warn("Auto-approval failed for permissionId={}, falling back to user confirmation: {}",
                    permissionId, e.getMessage());
            return false;
        }
    }

    private PermissionContext parsePermissionContext(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return PermissionContext.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(contextJson);
            String permission = text(node, "permission");
            return new PermissionContext(permission,
                    textList(node.path("patterns")),
                    textList(node.path("always")));
        } catch (Exception e) {
            return PermissionContext.empty();
        }
    }

    private boolean isAlwaysResolution(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return false;
        try {
            return "always".equals(text(objectMapper.readTree(payloadJson), "reply"));
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                addSplitValues(result, item.asText(""));
            }
        } else {
            addSplitValues(result, node.asText(""));
        }
        return result.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void addSplitValues(List<String> result, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return;
        for (String value : rawValue.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isBlank()) result.add(trimmed);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText("").trim();
    }

    private boolean wildcardMatches(String pattern, String value) {
        if (pattern == null || pattern.isBlank() || value == null) return false;
        if (pattern.equals(value)) return true;
        StringBuilder regex = new StringBuilder("^");
        for (char ch : pattern.toCharArray()) {
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '\\', '.', '[', ']', '(', ')', '{', '}', '+', '^', '$', '|' ->
                        regex.append('\\').append(ch);
                default -> regex.append(ch);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }

    private String resolutionPayload(String reply, String source,
                                     boolean autoApproved, String matchedConfirmationId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reply", normalizeReply(reply));
            payload.put("source", source);
            if (autoApproved) {
                payload.put("autoApproved", true);
            }
            if (matchedConfirmationId != null && !matchedConfirmationId.isBlank()) {
                payload.put("matchedConfirmationId", matchedConfirmationId);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"reply\":\"" + normalizeReply(reply) + "\"}";
        }
    }

    private void publishPermissionResolvedEvent(ConfirmationRequestEntity entity,
                                                String actionType,
                                                String actionDescription) {
        if (entity.getAgentSessionId() == null || entity.getAgentSessionId().isBlank()) {
            return;
        }
        String payloadJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("confirmationId", entity.getId());
            payload.put("actionType", actionType);
            payload.put("requestType", entity.getRequestType());
            payload.put("title", entity.getTitle());
            payload.put("question", entity.getContent());
            payload.put("actionDescription", actionDescription);
            if (entity.getResolutionComment() != null && !entity.getResolutionComment().isBlank()) {
                payload.put("comment", entity.getResolutionComment());
            }
            if (entity.getResolutionPayloadJson() != null && !entity.getResolutionPayloadJson().isBlank()) {
                payload.put("resolutionPayload", entity.getResolutionPayloadJson());
            }
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{\"confirmationId\":\"" + entity.getId() + "\"}";
        }

        runtimeEventService.publishEvent(new RuntimeEventDto(
                null,
                entity.getAgentSessionId(),
                entity.getWorkItemId(),
                entity.getWorkflowInstanceId(),
                entity.getWorkflowNodeInstanceId(),
                RuntimeEventType.CONFIRMATION_RESOLVED,
                RuntimeEventSource.BRIDGE,
                payloadJson,
                null
        ));
    }

    private String normalizeReply(String reply) {
        if ("always".equals(reply) || "reject".equals(reply)) return reply;
        return "once";
    }

    private record PermissionContext(String permission, List<String> patterns, List<String> always) {
        private static PermissionContext empty() {
            return new PermissionContext("", List.of(), List.of());
        }

        private List<String> requestPatterns() {
            return patterns.isEmpty() ? always : patterns;
        }

        private List<String> approvedPatterns() {
            return always.isEmpty() ? patterns : always;
        }
    }

    private record SessionPermissionApproval(String confirmationId) {}
}
