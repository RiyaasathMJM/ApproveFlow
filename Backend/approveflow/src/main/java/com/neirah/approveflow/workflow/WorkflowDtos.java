package com.neirah.approveflow.workflow;

import com.neirah.approveflow.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WorkflowDtos {
    private WorkflowDtos() {}

    public record ConditionDto(@NotBlank String field, @NotBlank String op, @NotNull BigDecimal value) {}

    public record StepRequest(@NotNull @Min(1) Integer sequence,
                              @NotNull Role approverRole,
                              @Valid ConditionDto condition,
                              @Min(1) Integer dueHours) {}

    public record CreateWorkflowRequest(@NotBlank @Size(max = 100) String name,
                                        @NotNull RequestType requestType,
                                        @NotEmpty @Valid List<StepRequest> steps) {}

    public record ActivationRequest(@NotNull Boolean active) {}

    public record StepResponse(Long id, int sequence, Role approverRole,
                               ConditionDto condition, int dueHours) {}

    public record VersionResponse(Long id, int versionNumber, Instant createdAt,
                                  List<StepResponse> steps) {}

    public record WorkflowResponse(Long id, String name, RequestType requestType,
                                   boolean active, VersionResponse currentVersion) {}
}