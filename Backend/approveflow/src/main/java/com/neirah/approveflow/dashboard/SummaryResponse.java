package com.neirah.approveflow.dashboard;

public record SummaryResponse(long pendingApprovals, long overdueApprovals,
                              long approved, long rejected, long returned,
                              long escalations, Double avgCompletionHours) {}