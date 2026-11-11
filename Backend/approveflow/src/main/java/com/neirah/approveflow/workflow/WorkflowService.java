package com.neirah.approveflow.workflow;

import com.neirah.approveflow.common.ApiException;
import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.workflow.WorkflowDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class WorkflowService {

    private static final Set<String> OPS = Set.of(">", ">=", "<", "<=", "==");
    private static final Set<String> FIELDS = Set.of("amount");

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVersionRepository versions;
    private final ObjectMapper mapper;

    public WorkflowService(WorkflowDefinitionRepository definitions,
                           WorkflowVersionRepository versions, ObjectMapper mapper) {
        this.definitions = definitions;
        this.versions = versions;
        this.mapper = mapper;
    }

    @Transactional
    public WorkflowResponse create(CreateWorkflowRequest req) {
        validate(req);
        String name = req.name().trim();

        WorkflowDefinition def = definitions.findByName(name).orElse(null);
        int nextVersion = 1;
        if (def == null) {
            def = new WorkflowDefinition();
            def.setName(name);
            def.setRequestType(req.requestType());
            def = definitions.save(def);
        } else {
            if (def.getRequestType() != req.requestType()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Workflow '" + name + "' already exists with type " + def.getRequestType());
            }
            nextVersion = versions.findTopByWorkflowDefinitionIdOrderByVersionNumberDesc(def.getId())
                    .map(v -> v.getVersionNumber() + 1).orElse(1);
        }

        WorkflowVersion version = new WorkflowVersion();
        version.setWorkflowDefinitionId(def.getId());
        version.setVersionNumber(nextVersion);
        for (StepRequest s : req.steps()) {
            WorkflowStep step = new WorkflowStep();
            step.setSequenceNo(s.sequence());
            step.setApproverRole(s.approverRole());
            step.setConditionJson(s.condition() == null ? null : mapper.writeValueAsString(normalize(s.condition())));
            step.setDueHours(s.dueHours() == null ? 48 : s.dueHours());
            version.getSteps().add(step);
        }
        version = versions.save(version);

        def.setCurrentVersionId(version.getId());   // new submissions-ku mattum
        return toResponse(def, version);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> list(boolean adminView) {
        List<WorkflowDefinition> defs = adminView ? definitions.findAll() : definitions.findByActiveTrue();
        return defs.stream().map(d -> toResponse(d, currentVersion(d))).toList();
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> history(Long id) {
        findDefinition(id);
        return versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(id)
                .stream().map(this::toVersionResponse).toList();
    }

    @Transactional
    public WorkflowResponse setActive(Long id, boolean active) {
        WorkflowDefinition def = findDefinition(id);
        def.setActive(active);
        return toResponse(def, currentVersion(def));
    }

    private void validate(CreateWorkflowRequest req) {
        Set<Integer> seen = new HashSet<>();
        for (StepRequest s : req.steps()) {
            if (!seen.add(s.sequence())) {
                throw invalid("Duplicate step sequence: " + s.sequence());
            }
            if (s.approverRole() == Role.REQUESTER) {
                throw invalid("Invalid approver role: " + s.approverRole());
            }
            if (s.condition() != null) {
                ConditionDto c = s.condition();
                if (!FIELDS.contains(c.field())) throw invalid("Unsupported condition field: " + c.field());
                if (!OPS.contains(c.op())) throw invalid("Unsupported condition operator: " + c.op());
            }
        }
        // valid path: first step unconditional, so every request has a route
        StepRequest first = req.steps().stream()
                .min(Comparator.comparing(StepRequest::sequence)).orElseThrow();
        if (first.condition() != null) {
            throw invalid("The first step must be unconditional so every request has a valid path");
        }
    }

    private ConditionDto normalize(ConditionDto c) {
        return new ConditionDto(c.field(), c.op(), c.value());
    }

    private ApiException invalid(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", msg);
    }

    private WorkflowDefinition findDefinition(Long id) {
        return definitions.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workflow not found"));
    }

    private WorkflowVersion currentVersion(WorkflowDefinition d) {
        return d.getCurrentVersionId() == null ? null : versions.findById(d.getCurrentVersionId()).orElse(null);
    }

    private WorkflowResponse toResponse(WorkflowDefinition d, WorkflowVersion v) {
        return new WorkflowResponse(d.getId(), d.getName(), d.getRequestType(), d.isActive(),
                v == null ? null : toVersionResponse(v));
    }

    private VersionResponse toVersionResponse(WorkflowVersion v) {
        List<StepResponse> steps = v.getSteps().stream().map(s -> new StepResponse(
                s.getId(), s.getSequenceNo(), s.getApproverRole(),
                s.getConditionJson() == null ? null : mapper.readValue(s.getConditionJson(), ConditionDto.class),
                s.getDueHours())).toList();
        return new VersionResponse(v.getId(), v.getVersionNumber(), v.getCreatedAt(), steps);
    }
}