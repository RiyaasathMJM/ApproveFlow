package com.neirah.approveflow.request;

import com.neirah.approveflow.audit.AuditAction;
import com.neirah.approveflow.audit.AuditService;
import com.neirah.approveflow.common.ApiException;
import com.neirah.approveflow.request.RequestDtos.*;
import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.user.UserRepository;
import com.neirah.approveflow.workflow.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class RequestService {

    private final RequestRepository requests;
    private final UserRepository users;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVersionRepository versions;
    private final WorkflowStepRepository steps;
    private final PayloadValidator payloadValidator;
    private final ConditionEvaluator evaluator;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public RequestService(RequestRepository requests, UserRepository users,
                          WorkflowDefinitionRepository definitions, WorkflowVersionRepository versions,
                          WorkflowStepRepository steps, PayloadValidator payloadValidator,
                          ConditionEvaluator evaluator, AuditService audit, ObjectMapper mapper) {
        this.requests = requests;
        this.users = users;
        this.definitions = definitions;
        this.versions = versions;
        this.steps = steps;
        this.payloadValidator = payloadValidator;
        this.evaluator = evaluator;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public RequestResponse create(User actor, CreateRequest dto) {
        payloadValidator.validate(dto.type(), dto.payload());

        Request r = new Request();
        r.setOwner(users.getReferenceById(actor.getId()));
        r.setType(dto.type());
        r.setTitle(dto.title().trim());
        r.setDescription(dto.description());
        r.setPayloadJson(mapper.writeValueAsString(dto.payload()));
        return toResponse(requests.save(r));
    }

    @Transactional
    public RequestResponse update(Long id, User actor, UpdateRequest dto) {
        Request r = loadOwned(id, actor);
        if (r.getStatus() != RequestStatus.DRAFT && r.getStatus() != RequestStatus.RETURNED) {
            throw conflict("Submitted requests cannot be modified until they are returned");
        }
        payloadValidator.validate(r.getType(), dto.payload());

        r.setTitle(dto.title().trim());
        r.setDescription(dto.description());
        r.setPayloadJson(mapper.writeValueAsString(dto.payload()));
        return toResponse(r);
    }

    @Transactional
    public void delete(Long id, User actor) {
        Request r = loadOwned(id, actor);
        if (r.getStatus() != RequestStatus.DRAFT) {
            throw conflict("Only drafts can be deleted");
        }
        requests.delete(r);
    }

    @Transactional
    public RequestResponse submit(Long id, User actor) {
        Request r = loadOwned(id, actor);
        RequestStatus from = r.getStatus();
        if (from != RequestStatus.DRAFT && from != RequestStatus.RETURNED) {
            throw conflict("Only draft or returned requests can be submitted");
        }

        Map<String, Object> payload = readPayload(r);
        payloadValidator.validate(r.getType(), payload);

        // First submission: active workflow-oda current version-a snapshot pidikkum.
        // Resubmit: pazhaya version-laye irukkum (in-flight request retains version, T08).
        if (from == RequestStatus.DRAFT) {
            WorkflowDefinition def = definitions
                    .findFirstByRequestTypeAndActiveTrueOrderByIdAsc(r.getType())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "NO_ACTIVE_WORKFLOW", "No active workflow for " + r.getType()));
            r.setWorkflowVersionId(def.getCurrentVersionId());
        }

        WorkflowVersion version = versions.findById(r.getWorkflowVersionId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "NO_ACTIVE_WORKFLOW", "Workflow version not found"));

        WorkflowStep first = version.getSteps().stream()
                .filter(s -> evaluator.matches(s.getConditionJson(), payload))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "NO_ROUTE", "No approval step applies to this request"));

        Instant now = Instant.now();
        r.setStatus(RequestStatus.PENDING_APPROVAL);
        r.setCycle(r.getCycle() + 1);
        r.setCurrentStepId(first.getId());
        r.setCurrentStepDueAt(now.plus(Duration.ofHours(first.getDueHours())));
        if (r.getSubmittedAt() == null) r.setSubmittedAt(now);

        audit.record(r.getId(), actor.getId(),
                from == RequestStatus.DRAFT ? AuditAction.SUBMIT : AuditAction.RESUBMIT,
                from, RequestStatus.PENDING_APPROVAL, null,
                Map.of("cycle", r.getCycle(),
                        "stepId", first.getId(),
                        "workflowVersionId", r.getWorkflowVersionId()));

        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public Page<RequestResponse> list(User actor, RequestStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        boolean admin = isAdmin(actor);

        Page<Request> result;
        if (admin) {
            result = status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable);
        } else {
            result = status == null
                    ? requests.findByOwnerId(actor.getId(), pageable)
                    : requests.findByOwnerIdAndStatus(actor.getId(), status, pageable);
        }
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RequestDetailResponse get(Long id, User actor) {
        Request r = requests.findById(id)
                .filter(x -> canView(x, actor))
                .orElseThrow(this::notFound);
        return new RequestDetailResponse(toResponse(r), audit.timeline(r.getId()));
    }

    // Approval module-la assigned approver-um inge add aagum
    private boolean canView(Request r, User actor) {
        return isAdmin(actor) || r.getOwner().getId().equals(actor.getId());
    }

    private boolean isAdmin(User actor) {
        return actor.getRoles().contains(Role.ADMIN);
    }

    private Request loadOwned(Long id, User actor) {
        return requests.findById(id)
                .filter(r -> r.getOwner().getId().equals(actor.getId()))
                .orElseThrow(this::notFound);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(Request r) {
        return mapper.readValue(r.getPayloadJson(), Map.class);
    }

    private RequestResponse toResponse(Request r) {
        Role stepRole = r.getCurrentStepId() == null ? null
                : steps.findById(r.getCurrentStepId()).map(WorkflowStep::getApproverRole).orElse(null);
        return new RequestResponse(r.getId(), r.getOwner().getId(), r.getOwner().getName(),
                r.getType(), r.getTitle(), r.getDescription(), readPayload(r), r.getStatus(),
                r.getWorkflowVersionId(), r.getCurrentStepId(), stepRole, r.getCycle(),
                r.getCurrentStepDueAt(), r.getSubmittedAt(), r.getCompletedAt(), r.getCreatedAt());
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Request not found");
    }

    private ApiException conflict(String msg) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", msg);
    }
}