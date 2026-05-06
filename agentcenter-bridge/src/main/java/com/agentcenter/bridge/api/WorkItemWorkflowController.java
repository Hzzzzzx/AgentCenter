package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.StartWorkflowRequest;
import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.application.WorkflowCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemWorkflowController {

    private final WorkflowCommandService workflowCommandService;

    public WorkItemWorkflowController(WorkflowCommandService workflowCommandService) {
        this.workflowCommandService = workflowCommandService;
    }

    @PostMapping("/{id}/start-workflow")
    public StartWorkflowResponse startWorkflow(@PathVariable String id,
                                                @RequestBody(required = false) @Valid StartWorkflowRequest request) {
        return workflowCommandService.startWorkflow(id, request);
    }
}
