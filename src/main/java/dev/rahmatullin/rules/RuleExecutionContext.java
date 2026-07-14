package dev.rahmatullin.rules;

import dev.rahmatullin.domain.WarehouseState;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Getter
@Builder
public class RuleExecutionContext {
    private static final ThreadLocal<RuleExecutionContext> CURRENT = new ThreadLocal<>();

    private final String ruleId;
    private final String ruleDescription;
    private final WarehouseState state;
    private final String triggeringEvent;

    @Builder.Default
    private final Map<String, Object> facts = new HashMap<>();

    @Builder.Default
    private final List<Map<String, Object>> evaluatedConditions = new ArrayList<>();

    @Builder.Default
    private final List<String> messages = new ArrayList<>();

    @Builder.Default
    private final Set<String> producedEvents = new LinkedHashSet<>();

    public RuleExecutionContext(String ruleId, String ruleDescription, WarehouseState state, String triggeringEvent) {
        this.ruleId = ruleId;
        this.ruleDescription = ruleDescription;
        this.state = state;
        this.triggeringEvent = triggeringEvent;
        this.facts = new HashMap<>();
        this.evaluatedConditions = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.producedEvents = new LinkedHashSet<>();
    }

    private RuleExecutionContext(String ruleId, String ruleDescription, WarehouseState state, String triggeringEvent,
                                 Map<String, Object> facts, List<Map<String, Object>> evaluatedConditions,
                                 List<String> messages, Set<String> producedEvents) {
        this.ruleId = ruleId;
        this.ruleDescription = ruleDescription;
        this.state = state;
        this.triggeringEvent = triggeringEvent;
        this.facts = facts != null ? facts : new HashMap<>();
        this.evaluatedConditions = evaluatedConditions != null ? evaluatedConditions : new ArrayList<>();
        this.messages = messages != null ? messages : new ArrayList<>();
        this.producedEvents = producedEvents != null ? producedEvents : new LinkedHashSet<>();
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

    public void setFact(String key, Object value) {
        facts.put(key, value);
    }

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