package com.agentcenter.bridge.api;

import com.agentcenter.bridge.api.dto.CreateWorkItemRequest;
import com.agentcenter.bridge.api.dto.UpdateWorkItemRequest;
import com.agentcenter.bridge.api.dto.WorkItemDto;
import com.agentcenter.bridge.application.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @GetMapping
    public List<WorkItemDto> list() {
        return workItemService.listWorkItems();
    }

    @GetMapping("/{id}")
    public WorkItemDto get(@PathVariable String id) {
        return workItemService.getWorkItem(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItemDto create(@RequestBody @Valid CreateWorkItemRequest request) {
        return workItemService.createWorkItem(request);
    }

    @PutMapping("/{id}")
    public WorkItemDto update(@PathVariable String id, @RequestBody @Valid UpdateWorkItemRequest request) {
        return workItemService.updateWorkItem(id, request);
    }
}
