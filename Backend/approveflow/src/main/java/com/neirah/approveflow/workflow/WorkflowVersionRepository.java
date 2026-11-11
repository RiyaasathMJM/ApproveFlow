package com.neirah.approveflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
    Optional<WorkflowVersion> findTopByWorkflowDefinitionIdOrderByVersionNumberDesc(Long definitionId);
    List<WorkflowVersion> findByWorkflowDefinitionIdOrderByVersionNumberDesc(Long definitionId);
}
