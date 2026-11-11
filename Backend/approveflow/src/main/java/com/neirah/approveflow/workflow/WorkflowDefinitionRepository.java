package com.neirah.approveflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
    Optional<WorkflowDefinition> findByName(String name);
    List<WorkflowDefinition> findByActiveTrue();
    Optional<WorkflowDefinition> findFirstByRequestTypeAndActiveTrueOrderByIdAsc(RequestType type);
}