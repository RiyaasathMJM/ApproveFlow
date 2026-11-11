package com.neirah.approveflow.escalation;

import com.neirah.approveflow.request.RequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EscalationService {

    private final RequestRepository requests;
    private final EscalationWorker worker;

    public EscalationService(RequestRepository requests, EscalationWorker worker) {
        this.requests = requests;
        this.worker = worker;
    }

    // Idhula @Transactional podaadheenga. Ovvoru request thani transaction-la nadakkanum
    public EscalationResult run() {
        List<Long> ids = requests.findOverdueIds(Instant.now());
        int escalated = 0;
        for (Long id : ids) {
            try {
                if (worker.escalate(id)) escalated++;
            } catch (DataIntegrityViolationException ex) {
                // rendu job ore nerathula odinaal, unique constraint rendavadhai block pannum
            }
        }
        return new EscalationResult(ids.size(), escalated, ids.size() - escalated);
    }
}