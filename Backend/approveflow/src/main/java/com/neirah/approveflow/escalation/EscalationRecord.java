package com.neirah.approveflow.escalation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "escalation_records")
@Getter @Setter
public class EscalationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "step_id", nullable = false, updatable = false)
    private Long stepId;

    @Column(nullable = false, updatable = false)
    private int cycle;

    @Column(name = "escalated_at", nullable = false, updatable = false)
    private Instant escalatedAt;

    @PrePersist
    void onCreate() { escalatedAt = Instant.now(); }
}
