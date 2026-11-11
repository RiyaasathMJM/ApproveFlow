package com.neirah.approveflow.approval;

import com.neirah.approveflow.common.PageResponse;
import com.neirah.approveflow.request.RequestDtos.RequestResponse;
import com.neirah.approveflow.user.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) { this.service = service; }

    @GetMapping("/api/approvals/pending")
    @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")
    public PageResponse<RequestResponse> pending(@AuthenticationPrincipal User user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.pending(user, page, size));
    }

    @PostMapping("/api/requests/{id}/decisions")
    @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")
    public RequestResponse decide(@PathVariable Long id, @AuthenticationPrincipal User user,
                                  @Valid @RequestBody DecisionRequest body) {
        return service.decide(id, user, body);
    }
}