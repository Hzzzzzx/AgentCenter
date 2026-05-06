package com.agentcenter.bridge.api;

import java.util.List;
import java.util.Map;

import com.agentcenter.bridge.api.dto.ConfirmationRequestDto;
import com.agentcenter.bridge.api.dto.ResolveConfirmationRequest;
import com.agentcenter.bridge.application.ConfirmationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/confirmations")
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    public ConfirmationController(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @GetMapping
    public List<ConfirmationRequestDto> list(@RequestParam(required = false) String status) {
        if (status != null) {
            return confirmationService.listByStatus(status);
        }
        return confirmationService.listPending();
    }

    @GetMapping("/{id}")
    public ConfirmationRequestDto get(@PathVariable String id) {
        return confirmationService.getById(id);
    }

    @PostMapping("/{id}/enter-session")
    public ConfirmationRequestDto enterSession(@PathVariable String id) {
        return confirmationService.enterSession(id);
    }

    @PostMapping("/{id}/resolve")
    public ConfirmationRequestDto resolve(@PathVariable String id,
                                          @RequestBody ResolveConfirmationRequest request) {
        return confirmationService.resolve(id, request);
    }

    @PostMapping("/{id}/reject")
    public ConfirmationRequestDto reject(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, String> body) {
        return confirmationService.reject(id, body != null ? body.get("comment") : null);
    }
}
