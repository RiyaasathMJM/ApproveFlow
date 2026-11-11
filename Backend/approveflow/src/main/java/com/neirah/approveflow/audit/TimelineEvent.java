package com.neirah.approveflow.audit;

import com.neirah.approveflow.request.RequestStatus;
import java.time.Instant;

public record TimelineEvent(Long id, AuditAction action, Long actorId, String actorName,
                            RequestStatus fromStatus, RequestStatus toStatus,
                            String comment, Instant createdAt) {}