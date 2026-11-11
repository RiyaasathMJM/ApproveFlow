package com.neirah.approveflow.dashboard;

import com.neirah.approveflow.escalation.EscalationRecordRepository;
import com.neirah.approveflow.request.RequestRepository;
import com.neirah.approveflow.request.RequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DashboardService {

    private final RequestRepository requests;
    private final EscalationRecordRepository escalations;

    public DashboardService(RequestRepository requests, EscalationRecordRepository escalations) {
        this.requests = requests;
        this.escalations = escalations;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary() {
        Number avgSeconds = requests.averageApprovalSeconds();
        Double avgHours = avgSeconds == null ? null
                : Math.round(avgSeconds.doubleValue() / 3600.0 * 100.0) / 100.0;

        return new SummaryResponse(
                requests.countByStatus(RequestStatus.PENDING_APPROVAL),
                requests.countOverdue(Instant.now()),
                requests.countByStatus(RequestStatus.APPROVED),
                requests.countByStatus(RequestStatus.REJECTED),
                requests.countByStatus(RequestStatus.RETURNED),
                escalations.count(),
                avgHours);
    }
}