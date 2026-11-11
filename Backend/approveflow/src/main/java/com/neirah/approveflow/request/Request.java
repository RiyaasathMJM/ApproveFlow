package com.neirah.approveflow.request;

import com.neirah.approveflow.user.User;
import com.neirah.approveflow.workflow.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "requests")
@Getter @Setter
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "workflow_version_id")
    private Long workflowVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestType type;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(name = "current_step_id")
    private Long currentStepId;

    @Column(nullable = false)
    private int cycle = 0;

    @Column(name = "current_step_due_at")
    private Instant currentStepDueAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
