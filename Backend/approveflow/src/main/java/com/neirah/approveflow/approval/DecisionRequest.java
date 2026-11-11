package com.neirah.approveflow.approval;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@NotNull DecisionAction action, @Size(max = 2000) String comment) {}