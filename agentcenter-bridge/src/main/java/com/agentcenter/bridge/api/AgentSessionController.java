package com.agentcenter.bridge.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentcenter.bridge.api.dto.AgentMessageDto;
import com.agentcenter.bridge.api.dto.AgentSessionDto;
import com.agentcenter.bridge.api.dto.SendMessageRequest;
import com.agentcenter.bridge.application.AgentSessionService;

@RestController
@RequestMapping("/api/agent-sessions")
public class AgentSessionController {

    private final AgentSessionService sessionService;
    private final String defaultRuntimeType;

    public AgentSessionController(AgentSessionService sessionService,
                                  @Value("${agentcenter.runtime.default-type:OPENCODE}") String defaultRuntimeType) {
        this.sessionService = sessionService;
        this.defaultRuntimeType = defaultRuntimeType;
    }

    @GetMapping
    public List<AgentSessionDto> listSessions() {
        return sessionService.listSessions();
    }

    @PostMapping
    public ResponseEntity<AgentSessionDto> createSession(@RequestBody Map<String, String> body) {
        var sessionType = com.agentcenter.bridge.domain.session.SessionType
                .valueOf(body.getOrDefault("sessionType", "GENERAL"));
        var runtimeType = com.agentcenter.bridge.domain.runtime.RuntimeType
                .valueOf(body.getOrDefault("runtimeType", defaultRuntimeType).toUpperCase());
        String title = body.get("title");
        String workItemId = body.get("workItemId");
        String workflowInstanceId = body.get("workflowInstanceId");

        AgentSessionDto created = sessionService.createSession(
                sessionType, title, workItemId, workflowInstanceId, runtimeType);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public AgentSessionDto getSession(@PathVariable String id) {
        return sessionService.getSession(id);
    }

    @GetMapping("/{id}/messages")
    public List<AgentMessageDto> getMessages(@PathVariable String id) {
        return sessionService.getMessages(id);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<AgentMessageDto> sendMessage(@PathVariable String id,
                                                        @RequestBody SendMessageRequest request) {
        AgentMessageDto created = sessionService.sendMessage(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
