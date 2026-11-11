package com.neirah.approveflow.workflow;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_versions")
@Getter @Setter
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_definition_id", nullable = false)
    private Long workflowDefinitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "version_id", nullable = false)
    @OrderBy("sequenceNo ASC")
    private List<WorkflowStep> steps = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
