package com.neirah.approveflow.escalation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/escalations")
public class EscalationController {

    private final EscalationService service;

    public EscalationController(EscalationService service) { this.service = service; }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public EscalationResult run() {
        return service.run();
    }
}