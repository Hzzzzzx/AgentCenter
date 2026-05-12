package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.BatchStartWorkflowsRequest;
import com.agentcenter.bridge.api.dto.BatchStartWorkflowsResponse;
import com.agentcenter.bridge.api.dto.RestartWorkflowRequest;
import com.agentcenter.bridge.api.dto.StartWorkflowRequest;
import com.agentcenter.bridge.api.dto.StartWorkflowResponse;
import com.agentcenter.bridge.api.dto.WorkflowVersionDto;
import com.agentcenter.bridge.application.WorkflowCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/{id}/restart-workflow")
    public StartWorkflowResponse restartWorkflow(@PathVariable String id,
                                                  @RequestBody(required = false) @Valid RestartWorkflowRequest request) {
        return workflowCommandService.restartWorkflow(id, request);
    }

    @GetMapping("/{id}/workflow-versions")
    public List<WorkflowVersionDto> listWorkflowVersions(@PathVariable String id) {
        return workflowCommandService.listWorkflowVersions(id);
    }

    @PostMapping("/start-workflows")
    public BatchStartWorkflowsResponse startWorkflows(@RequestBody @Valid BatchStartWorkflowsRequest request) {
        return workflowCommandService.startWorkflows(request);
    }
}
