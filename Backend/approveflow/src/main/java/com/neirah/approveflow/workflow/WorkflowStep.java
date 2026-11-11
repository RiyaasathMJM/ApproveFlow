package com.neirah.approveflow.workflow;

import com.neirah.approveflow.user.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workflow_steps")
@Getter @Setter
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 20)
    private Role approverRole;

    @Column(name = "condition_json")
    private String conditionJson;

    @Column(name = "due_hours", nullable = false)
    private int dueHours = 48;
}
