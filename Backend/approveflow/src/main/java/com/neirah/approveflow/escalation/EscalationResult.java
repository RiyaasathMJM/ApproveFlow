package com.neirah.approveflow.escalation;

public record EscalationResult(int overdueFound, int newlyEscalated, int alreadyEscalated) {}