package com.neirah.approveflow.escalation;

import com.neirah.approveflow.audit.AuditAction;
import com.neirah.approveflow.audit.AuditService;
import com.neirah.approveflow.request.Request;
import com.neirah.approveflow.request.RequestRepository;
import com.neirah.approveflow.request.RequestStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
public class EscalationWorker {

    private final RequestRepository requests;
    private final EscalationRecordRepository records;
    private final AuditService audit;

    public EscalationWorker(RequestRepository requests, EscalationRecordRepository records,
                            AuditService audit) {
        this.requests = requests;
        this.records = records;
        this.audit = audit;
    }

    @Transactional
    public boolean escalate(Long requestId) {
        Request r = requests.findById(requestId).orElse(null);

        // job odumbodhu state maariyirukkalaam, so thirumba check
        if (r == null
                || r.getStatus() != RequestStatus.PENDING_APPROVAL
                || r.getCurrentStepId() == null
                || r.getCurrentStepDueAt() == null
                || !r.getCurrentStepDueAt().isBefore(Instant.now())) {
            return false;
        }
        if (records.existsByRequestIdAndStepIdAndCycle(r.getId(), r.getCurrentStepId(), r.getCycle())) {
            return false;   // already escalated, idempotent
        }

        EscalationRecord rec = new EscalationRecord();
        rec.setRequestId(r.getId());
        rec.setStepId(r.getCurrentStepId());
        rec.setCycle(r.getCycle());
        records.saveAndFlush(rec);

        // actor = null (system). Status maaraadhu: PENDING_APPROVAL to PENDING_APPROVAL
        audit.record(r.getId(), null, AuditAction.ESCALATE,
                RequestStatus.PENDING_APPROVAL, RequestStatus.PENDING_APPROVAL,
                "Step is overdue",
                Map.of("cycle", r.getCycle(),
                        "stepId", r.getCurrentStepId(),
                        "dueAt", r.getCurrentStepDueAt().toString()));
        return true;
    }
}