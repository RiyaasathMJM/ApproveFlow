package com.neirah.approveflow.workflow;

import com.neirah.approveflow.workflow.WorkflowDtos.ConditionDto;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ConditionEvaluator {

    private final ObjectMapper mapper;

    public ConditionEvaluator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean matches(String conditionJson, Map<String, Object> payload) {
        if (conditionJson == null || conditionJson.isBlank()) return true;   // unconditional step

        ConditionDto c = mapper.readValue(conditionJson, ConditionDto.class);
        Object raw = payload.get(c.field());
        if (raw == null) return false;

        int cmp = new BigDecimal(String.valueOf(raw)).compareTo(c.value());
        return switch (c.op()) {
            case ">"  -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<"  -> cmp < 0;
            case "<=" -> cmp <= 0;
            case "==" -> cmp == 0;
            default   -> false;
        };
    }
}