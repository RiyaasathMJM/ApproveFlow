package com.neirah.approveflow.escalation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EscalationRecordRepository extends JpaRepository<EscalationRecord, Long> {
    boolean existsByRequestIdAndStepIdAndCycle(Long requestId, Long stepId, int cycle);
}