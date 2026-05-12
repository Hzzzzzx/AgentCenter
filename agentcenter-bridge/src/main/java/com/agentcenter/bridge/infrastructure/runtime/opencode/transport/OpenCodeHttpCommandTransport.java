package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agentcenter.bridge.application.runtime.protocol.RuntimeAckEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandEnvelope;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeCommandTypes;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeEnvelopeKind;
import com.agentcenter.bridge.application.runtime.protocol.RuntimeProtocolVersion;
import com.agentcenter.bridge.application.runtime.transport.RuntimeCommandTransport;
import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;
import com.agentcenter.bridge.domain.runtime.RuntimeType;
import com.agentcenter.bridge.infrastructure.runtime.opencode.OpenCodeTextEncoding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * HTTP-based {@link RuntimeCommandTransport} for OpenCode.
 * Sends commands to {@code opencode serve} via its REST API and maps
 * responses to {@link RuntimeAckEnvelope}.
 *
 * <p>Connection details (baseUrl, workingDirectory) are extracted from
 * each command's payload at send-time so they are always current,
 * even if the opencode process restarts between calls.</p>
 */
public class OpenCodeHttpCommandTransport implements RuntimeCommandTransport {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeHttpCommandTransport.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenCodeHttpCommandTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command) {
        return send(command, Duration.ofSeconds(30));
    }

    @Override
    public RuntimeAckEnvelope send(RuntimeCommandEnvelope command, Duration timeout) {
        return switch (command.type()) {
            case RuntimeCommandTypes.SESSION_ENSURE -> handleSessionCreate(command, timeout);
            case RuntimeCommandTypes.CONVERSATION_MESSAGE_SEND -> handleConversationSend(command, timeout);
            case RuntimeCommandTypes.CONVERSATION_CANCEL -> handleConversationCancel(command, timeout);
            case RuntimeCommandTypes.PERMISSION_RESPOND -> handlePermissionRespond(command, timeout);
            case RuntimeCommandTypes.QUESTION_REPLY -> handleQuestionReply(command, timeout);
            case RuntimeCommandTypes.QUESTION_REJECT -> handleQuestionReject(command, timeout);
            default -> RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "Unsupported command type: " + command.type());
        };
    }

    private RuntimeAckEnvelope handleSessionCreate(RuntimeCommandEnvelope command, Duration timeout) {
        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", payload.path("title").asText("AgentCenter Session"));
        body.set("permission", payload.path("permission"));

        HttpRequest request = buildPost(baseUrl, "/session", body, cwd, timeout);
        HttpResponse<String> response = execute(request);
        JsonNode result = parseBody(response.body());

        String sessionId = result.path("id").asText("");
        if (sessionId.isEmpty()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "opencode session response missing id: " + response.body());
        }

        ObjectNode ackPayload = objectMapper.createObjectNode();
        ackPayload.put("sessionId", sessionId);
        return buildAck(command, ackPayload);
    }

    private RuntimeAckEnvelope handleConversationSend(RuntimeCommandEnvelope command, Duration timeout) {
        String sessionId = command.runtimeSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "runtimeSessionId is required for CONVERSATION_SEND");
        }

        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent", payload.path("agent").asText("build"));
        body.set("parts", payload.path("parts"));

        HttpRequest request = buildPost(baseUrl, "/session/" + sessionId + "/prompt_async", body, cwd, timeout);
        execute(request);

        return buildAck(command, null);
    }

    private RuntimeAckEnvelope handleConversationCancel(RuntimeCommandEnvelope command, Duration timeout) {
        String sessionId = command.runtimeSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "runtimeSessionId is required for CONVERSATION_CANCEL");
        }

        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");

        HttpRequest request = buildPostNoBody(baseUrl, "/session/" + sessionId + "/abort", cwd, timeout);
        execute(request);
        return buildAck(command, null);
    }

    private RuntimeAckEnvelope handlePermissionRespond(RuntimeCommandEnvelope command, Duration timeout) {
        String sessionId = command.runtimeSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "runtimeSessionId is required for PERMISSION_RESPOND");
        }

        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");
        String permissionId = payload.path("permissionId").asText("");
        String reply = payload.path("reply").asText("");
        if (reply.isBlank()) {
            reply = payload.path("approved").asBoolean(true) ? "once" : "reject";
        }
        reply = normalizePermissionReply(reply);
        if (permissionId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "permissionId is required for PERMISSION_RESPOND");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("response", reply);

        HttpRequest request = buildPost(baseUrl, "/session/" + sessionId + "/permissions/" + permissionId, body, cwd, timeout);
        execute(request);
        return buildAck(command, null);
    }

    private RuntimeAckEnvelope handleQuestionReply(RuntimeCommandEnvelope command, Duration timeout) {
        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");
        String requestId = payload.path("requestId").asText("");
        if (requestId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "requestId is required for QUESTION_REPLY");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("answers", payload.path("answers"));

        HttpRequest request = buildPost(baseUrl, "/question/" + requestId + "/reply", body, cwd, timeout);
        execute(request);
        return buildAck(command, null);
    }

    private RuntimeAckEnvelope handleQuestionReject(RuntimeCommandEnvelope command, Duration timeout) {
        JsonNode payload = command.payload();
        String baseUrl = payload.path("baseUrl").asText("");
        String cwd = payload.path("workingDirectory").asText("");
        String requestId = payload.path("requestId").asText("");
        if (requestId.isBlank()) {
            return RuntimeAckEnvelope.nack(command.messageId(), RuntimeType.OPENCODE,
                    "requestId is required for QUESTION_REJECT");
        }

        HttpRequest request = buildPostNoBody(baseUrl, "/question/" + requestId + "/reject", cwd, timeout);
        execute(request);
        return buildAck(command, null);
    }

    public JsonNode fetchMessages(String baseUrl, String workingDirectory, String opencodeSessionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + opencodeSessionId + "/message"))
                .header("x-opencode-directory", workingDirectory)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(OpenCodeTextEncoding.WIRE_CHARSET));
        } catch (IOException e) {
            log.warn("Failed to fetch messages for {}: {}", opencodeSessionId, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (response.statusCode() >= 300) {
            log.warn("Failed to fetch messages for {}: HTTP {}", opencodeSessionId, response.statusCode());
            return null;
        }
        return parseBody(response.body());
    }

    private HttpRequest buildPost(String baseUrl, String path, ObjectNode body,
                                  String workingDirectory, Duration timeout) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeTransportException("JSON serialization failed: " + e.getMessage(), e, false);
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-opencode-directory", workingDirectory)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(json, OpenCodeTextEncoding.WIRE_CHARSET))
                .build();
    }

    private HttpRequest buildPostNoBody(String baseUrl, String path,
                                        String workingDirectory, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("x-opencode-directory", workingDirectory)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private HttpResponse<String> execute(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(OpenCodeTextEncoding.WIRE_CHARSET));
        } catch (IOException e) {
            throw new RuntimeTransportException(
                    "HTTP request to opencode serve failed: " + e.getMessage(), e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeTransportException(
                    "HTTP request to opencode serve interrupted: " + e.getMessage(), e, true);
        }

        int status = response.statusCode();
        if (status >= 500) {
            throw new RuntimeTransportException(
                    "opencode serve returned HTTP " + status + ": " + response.body(), null, true);
        }
        if (status >= 400) {
            throw new RuntimeTransportException(
                    "opencode serve returned HTTP " + status + ": " + response.body(), null, false);
        }
        if (status >= 300) {
            throw new RuntimeTransportException(
                    "opencode serve returned HTTP " + status + ": " + response.body(), null, false);
        }
        String contentType = response.headers() != null
                ? response.headers().firstValue("Content-Type").orElse("")
                : "";
        if (contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            throw new RuntimeTransportException(
                    "opencode serve returned HTML for API request " + request.uri(), null, false);
        }
        return response;
    }

    private JsonNode parseBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeTransportException("JSON parse failed: " + e.getMessage(), e, false);
        }
    }

    private String normalizePermissionReply(String reply) {
        if ("always".equals(reply) || "reject".equals(reply)) return reply;
        return "once";
    }

    private RuntimeAckEnvelope buildAck(RuntimeCommandEnvelope command, ObjectNode payload) {
        return new RuntimeAckEnvelope(
                RuntimeEnvelopeKind.ACK,
                RuntimeProtocolVersion.V1,
                null,
                UUID.randomUUID().toString(),
                command.messageId(),
                null,
                RuntimeType.OPENCODE,
                command.agentSessionId(),
                command.runtimeSessionId(),
                true,
                null,
                payload,
                OffsetDateTime.now()
        );
    }
}
