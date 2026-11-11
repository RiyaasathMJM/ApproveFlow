package com.neirah.approveflow.seed;

import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.workflow.RequestType;
import com.neirah.approveflow.workflow.WorkflowDefinitionRepository;
import com.neirah.approveflow.workflow.WorkflowDtos.*;
import com.neirah.approveflow.workflow.WorkflowService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("!test")
public class WorkflowSeeder implements CommandLineRunner {

    private final WorkflowService service;
    private final WorkflowDefinitionRepository definitions;

    public WorkflowSeeder(WorkflowService service, WorkflowDefinitionRepository definitions) {
        this.service = service;
        this.definitions = definitions;
    }

    @Override
    public void run(String... args) {
        if (definitions.findByName("Expense Claim").isEmpty()) {
            service.create(new CreateWorkflowRequest("Expense Claim", RequestType.EXPENSE, List.of(
                    new StepRequest(1, Role.MANAGER, null, 48),
                    new StepRequest(2, Role.FINANCE,
                            new ConditionDto("amount", ">", BigDecimal.valueOf(10000)), 48))));
        }
        if (definitions.findByName("Leave Request").isEmpty()) {
            service.create(new CreateWorkflowRequest("Leave Request", RequestType.LEAVE, List.of(
                    new StepRequest(1, Role.MANAGER, null, 48))));
        }
    }
}