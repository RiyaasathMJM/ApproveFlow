package com.neirah.approveflow.request;

import com.neirah.approveflow.common.ApiException;
import com.neirah.approveflow.workflow.RequestType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

@Component
public class PayloadValidator {

    private static final Set<String> LEAVE_TYPES = Set.of("ANNUAL", "SICK", "CASUAL", "OTHER");

    public void validate(RequestType type, Map<String, Object> p) {
        switch (type) {
            case EXPENSE -> validateExpense(p);
            case LEAVE -> validateLeave(p);
        }
    }

    private void validateExpense(Map<String, Object> p) {
        Object raw = p.get("amount");
        if (raw == null) throw invalid("amount is required");
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            throw invalid("amount must be a number");
        }
        if (amount.signum() <= 0) throw invalid("amount must be greater than 0");
    }

    private void validateLeave(Map<String, Object> p) {
        String leaveType = text(p, "leaveType");
        if (!LEAVE_TYPES.contains(leaveType.toUpperCase())) {
            throw invalid("leaveType must be one of " + LEAVE_TYPES);
        }
        LocalDate start = date(p, "startDate");
        LocalDate end = date(p, "endDate");
        if (end.isBefore(start)) throw invalid("endDate must not be before startDate");
        text(p, "reason");
    }

    private String text(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null || String.valueOf(v).isBlank()) throw invalid(key + " is required");
        return String.valueOf(v).trim();
    }

    private LocalDate date(Map<String, Object> p, String key) {
        try {
            return LocalDate.parse(text(p, key));
        } catch (DateTimeParseException ex) {
            throw invalid(key + " must be a date in yyyy-MM-dd format");
        }
    }

    private ApiException invalid(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", msg);
    }
}