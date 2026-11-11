package com.neirah.approveflow.request;

import com.neirah.approveflow.audit.TimelineEvent;
import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.workflow.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RequestDtos {
    private RequestDtos() {}

    public record CreateRequest(@NotNull RequestType type,
                                @NotBlank @Size(max = 200) String title,
                                @Size(max = 2000) String description,
                                @NotNull Map<String, Object> payload) {}

    public record UpdateRequest(@NotBlank @Size(max = 200) String title,
                                @Size(max = 2000) String description,
                                @NotNull Map<String, Object> payload) {}

    public record RequestResponse(Long id, Long ownerId, String ownerName, RequestType type,
                                  String title, String description, Map<String, Object> payload,
                                  RequestStatus status, Long workflowVersionId,
                                  Long currentStepId, Role currentStepRole, int cycle,
                                  Instant currentStepDueAt, Instant submittedAt,
                                  Instant completedAt, Instant createdAt) {}

    public record RequestDetailResponse(RequestResponse request, List<TimelineEvent> timeline) {}
}