package dev.rahmatullin.rules;

import dev.rahmatullin.domain.WarehouseState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RuleExecutionContext {
    private static final ThreadLocal<RuleExecutionContext> CURRENT = new ThreadLocal<>();

    private final String ruleId;
    private final String ruleDescription;
    private final WarehouseState state;
    private final String triggeringEvent;
    private final Map<String, Object> facts = new HashMap<>();
    private final List<Map<String, Object>> evaluatedConditions = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final Set<String> producedEvents = new LinkedHashSet<>();

    public RuleExecutionContext(String ruleId, String ruleDescription, WarehouseState state, String triggeringEvent) {
        this.ruleId = ruleId;
        this.ruleDescription = ruleDescription;
        this.state = state;
        this.triggeringEvent = triggeringEvent;
    }

    public static void bind(RuleExecutionContext ctx) {
        CURRENT.set(ctx);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<RuleExecutionContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public String getRuleId() { return ruleId; }
    public String getRuleDescription() { return ruleDescription; }
    public WarehouseState getState() { return state; }
    public String getTriggeringEvent() { return triggeringEvent; }
    public Map<String, Object> getFacts() { return facts; }
    public void setFact(String key, Object value) { facts.put(key, value); }
    public List<Map<String, Object>> getEvaluatedConditions() { return evaluatedConditions; }
    public List<String> getMessages() { return messages; }
    public Set<String> getProducedEvents() { return producedEvents; }

    public void addConditionResult(String expression, boolean result) {
        Map<String, Object> row = new HashMap<>();
        row.put("expression", expression);
        row.put("result", result);
        evaluatedConditions.add(row);
    }

    public void addMessage(String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }

    public void emitEvent(String event) {
        if (event != null && !event.isBlank()) {
            producedEvents.add(event);
        }
    }
}
