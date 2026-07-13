package dev.rahmatullin.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AuditEntry {
    private String traceId;
    private String ruleId;
    private String triggeringEvent;
    private Instant timestamp = Instant.now();
    private String decision; // APPLIED / SKIPPED / FAILED
    private int priority;
    private List<Map<String, Object>> evaluatedConditions;
    private Map<String, Object> facts;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private List<String> producedEvents;
    private String explanation;
    private String message;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTriggeringEvent() { return triggeringEvent; }
    public void setTriggeringEvent(String triggeringEvent) { this.triggeringEvent = triggeringEvent; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public List<Map<String, Object>> getEvaluatedConditions() { return evaluatedConditions; }
    public void setEvaluatedConditions(List<Map<String, Object>> evaluatedConditions) { this.evaluatedConditions = evaluatedConditions; }
    public Map<String, Object> getFacts() { return facts; }
    public void setFacts(Map<String, Object> facts) { this.facts = facts; }
    public Map<String, Object> getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(Map<String, Object> beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public Map<String, Object> getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(Map<String, Object> afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public List<String> getProducedEvents() { return producedEvents; }
    public void setProducedEvents(List<String> producedEvents) { this.producedEvents = producedEvents; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) {
        this.explanation = explanation;
        this.message = explanation;
    }
    public String getMessage() { return message; }
    public void setMessage(String message) {
        this.message = message;
        this.explanation = message;
    }
}
