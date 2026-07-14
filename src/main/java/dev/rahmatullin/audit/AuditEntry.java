package dev.rahmatullin.audit;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntry {
    private String traceId;
    private String ruleId;
    private String triggeringEvent;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String decision; // APPLIED / SKIPPED / FAILED
    private int priority;
    private List<Map<String, Object>> evaluatedConditions;
    private Map<String, Object> facts;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private List<String> producedEvents;
    private String explanation;
}
