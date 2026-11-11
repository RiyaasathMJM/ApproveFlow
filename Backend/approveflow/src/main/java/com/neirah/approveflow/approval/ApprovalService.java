package com.neirah.approveflow.approval;

import com.neirah.approveflow.audit.AuditAction;
import com.neirah.approveflow.audit.AuditService;
import com.neirah.approveflow.common.ApiException;
import com.neirah.approveflow.request.Request;
import com.neirah.approveflow.request.RequestDtos.RequestResponse;
import com.neirah.approveflow.request.RequestRepository;
import com.neirah.approveflow.request.RequestService;
import com.neirah.approveflow.request.RequestStatus;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.workflow.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ApprovalService {

    private final RequestRepository requests;
    private final WorkflowStepRepository steps;
    private final WorkflowVersionRepository versions;
    private final ApprovalDecisionRepository decisions;
    private final ConditionEvaluator evaluator;
    private final AuditService audit;
    private final RequestService requestService;
    private final ObjectMapper mapper;

    public ApprovalService(RequestRepository requests, WorkflowStepRepository steps,
                           WorkflowVersionRepository versions, ApprovalDecisionRepository decisions,
                           ConditionEvaluator evaluator, AuditService audit,
                           RequestService requestService, ObjectMapper mapper) {
        this.requests = requests;
        this.steps = steps;
        this.versions = versions;
        this.decisions = decisions;
        this.evaluator = evaluator;
        this.audit = audit;
        this.requestService = requestService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<RequestResponse> pending(User actor, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("currentStepDueAt"), Sort.Order.asc("id")));
        return requests.findPendingFor(actor.getId(), actor.getRoles(), pageable)
                .map(requestService::toResponse);
    }

    // Decision + status + audit: ore transaction. Edhaavadhu fail aana ellame rollback.
    @Transactional
    public RequestResponse decide(Long requestId, User actor, DecisionRequest dto) {
        Request r = requests.findById(requestId).orElseThrow(this::notFound);

        // 1. self-approval (T04)
        if (r.getOwner().getId().equals(actor.getId())) {
            throw forbidden("SELF_APPROVAL", "You cannot decide on your own request");
        }
        // 2. drafts other users-ku invisible
        if (r.getStatus() == RequestStatus.DRAFT) throw notFound();

        // 3. terminal / returned requests (T12)
        if (r.getStatus() != RequestStatus.PENDING_APPROVAL || r.getCurrentStepId() == null) {
            throw conflict("INVALID_TRANSITION", "Request is not awaiting your approval");
        }

        // 4. current step-ku intha user-ku role irukka (T03)
        WorkflowStep step = steps.findById(r.getCurrentStepId())
                .orElseThrow(() -> conflict("INVALID_TRANSITION", "Current step not found"));
        if (!actor.getRoles().contains(step.getApproverRole())) {
            throw forbidden("NOT_ASSIGNED", "This step is not assigned to you");
        }

        // 5. reject / return-ku reason kattaayam (T06)
        String comment = dto.comment() == null ? null : dto.comment().trim();
        if (dto.action() != DecisionAction.APPROVE && (comment == null || comment.isEmpty())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "A reason is required to reject or return a request");
        }

        // 6. duplicate decision (T05). DB unique constraint backstop-um irukku
        if (decisions.existsByRequestIdAndStepIdAndCycle(r.getId(), step.getId(), r.getCycle())) {
            throw conflict("DUPLICATE_DECISION", "This step has already been decided");
        }
        ApprovalDecision d = new ApprovalDecision();
        d.setRequestId(r.getId());
        d.setStepId(step.getId());
        d.setCycle(r.getCycle());
        d.setActorId(actor.getId());
        d.setAction(dto.action());
        d.setComment(comment);
        try {
            decisions.saveAndFlush(d);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("DUPLICATE_DECISION", "This step has already been decided");
        }

        // 7. transition
        Instant now = Instant.now();
        RequestStatus from = r.getStatus();
        RequestStatus to;
        AuditAction auditAction;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cycle", r.getCycle());
        meta.put("stepId", step.getId());
        meta.put("stepSequence", step.getSequenceNo());

        if (dto.action() == DecisionAction.APPROVE) {
            auditAction = AuditAction.APPROVE;
            Optional<WorkflowStep> next = nextStep(r, step);
            if (next.isPresent()) {
                to = RequestStatus.PENDING_APPROVAL;
                r.setCurrentStepId(next.get().getId());
                r.setCurrentStepDueAt(now.plus(Duration.ofHours(next.get().getDueHours())));
                meta.put("nextStepId", next.get().getId());
            } else {
                to = RequestStatus.APPROVED;
                complete(r, now);
            }
        } else if (dto.action() == DecisionAction.REJECT) {
            auditAction = AuditAction.REJECT;
            to = RequestStatus.REJECTED;
            complete(r, now);
        } else {
            auditAction = AuditAction.RETURN;
            to = RequestStatus.RETURNED;
            r.setCurrentStepId(null);
            r.setCurrentStepDueAt(null);
        }
        r.setStatus(to);

        audit.record(r.getId(), actor.getId(), auditAction, from, to, comment, meta);
        return requestService.toResponse(r);
    }

    private Optional<WorkflowStep> nextStep(Request r, WorkflowStep current) {
        WorkflowVersion version = versions.findById(r.getWorkflowVersionId())
                .orElseThrow(() -> conflict("INVALID_TRANSITION", "Workflow version not found"));
        Map<?, ?> payload = mapper.readValue(r.getPayloadJson(), Map.class);
        return version.getSteps().stream()
                .filter(s -> s.getSequenceNo() > current.getSequenceNo())
                .filter(s -> evaluator.matches(s.getConditionJson(), (Map<String, Object>) payload))
                .findFirst();
    }

    private void complete(Request r, Instant now) {
        r.setCurrentStepId(null);
        r.setCurrentStepDueAt(null);
        r.setCompletedAt(now);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Request not found");
    }

    private ApiException conflict(String code, String msg) {
        return new ApiException(HttpStatus.CONFLICT, code, msg);
    }

    private ApiException forbidden(String code, String msg) {
        return new ApiException(HttpStatus.FORBIDDEN, code, msg);
    }
}
