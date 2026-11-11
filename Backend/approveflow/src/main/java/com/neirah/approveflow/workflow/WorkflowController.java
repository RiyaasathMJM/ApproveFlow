package com.neirah.approveflow.workflow;

import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.workflow.WorkflowDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WorkflowResponse create(@Valid @RequestBody CreateWorkflowRequest req) {
        return service.create(req);
    }

    @GetMapping
    public List<WorkflowResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user.getRoles().contains(Role.ADMIN));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<VersionResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PatchMapping("/{id}/activation")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkflowResponse activation(@PathVariable Long id, @Valid @RequestBody ActivationRequest req) {
        return service.setActive(id, req.active());
    }
}
