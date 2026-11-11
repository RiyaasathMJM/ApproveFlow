package com.neirah.approveflow.approval;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, Long> {
    boolean existsByRequestIdAndStepIdAndCycle(Long requestId, Long stepId, int cycle);
    boolean existsByRequestIdAndActorId(Long requestId, Long actorId);
}