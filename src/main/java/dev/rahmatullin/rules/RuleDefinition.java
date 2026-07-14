package dev.rahmatullin.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {
    private String id;
    private String description;

    @Builder.Default
    private int priority = 0;

    private List<String> triggers;

    @Builder.Default
    private boolean active = true;

    private List<String> conditions;
    /**
     * JEXL scripts executed sequentially in one shared context.
     * Example: "robotId = f.firstFreeRobotId(state)", "f.assignRobotToOrder(state, robotId, orderId)"
     */
    private List<String> actions;
    private List<String> producedEvents;
    private Map<String, Object> meta;
}
