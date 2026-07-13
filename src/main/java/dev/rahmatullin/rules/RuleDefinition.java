package dev.rahmatullin.rules;

import java.util.List;
import java.util.Map;

public class RuleDefinition {
    private String id;
    private String description;
    private int priority = 0;
    private List<String> triggers;
    private boolean active = true;
    private List<String> conditions;
    /**
     * JEXL scripts executed sequentially in one shared context.
     * Example: "robotId = f.firstFreeRobotId(state)", "f.assignRobotToOrder(state, robotId, orderId)"
     */
    private List<String> actions;
    private List<String> producedEvents;
    private Map<String, Object> meta;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public List<String> getTriggers() { return triggers; }
    public void setTriggers(List<String> triggers) { this.triggers = triggers; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
    public List<String> getProducedEvents() { return producedEvents; }
    public void setProducedEvents(List<String> producedEvents) { this.producedEvents = producedEvents; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
}
