package dev.rahmatullin.rules;

import dev.rahmatullin.audit.AuditService;
import dev.rahmatullin.audit.AuditEntry;
import dev.rahmatullin.domain.WarehouseState;
import dev.rahmatullin.engine.MivarRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Wrapper that records audit entries for rule applicability and execution.
 */
public class AuditedRuleWrapper implements MivarRule {
    private final String id;
    private final AuditService auditService;
    private final DataDrivenRule delegate;
    private volatile boolean active = true;

    public AuditedRuleWrapper(String id, DataDrivenRule delegate, AuditService auditService) {
        this.id = id;
        this.delegate = delegate;
        this.auditService = auditService;
    }

    public void setActive(boolean active) { this.active = active; }
    public void updateDefinition(RuleDefinition def) { this.delegate.setDefinition(def); }
    public RuleDefinition getDefinition() { return delegate.getDefinition(); }

    @Override
    public Set<String> getTriggers() {
        if (!active) return Collections.emptySet();
        return delegate.getTriggers();
    }

    @Override
    public boolean isApplicable(WarehouseState state) {
        RuleDefinition def = getDefinition();
        RuleExecutionContext ctx = new RuleExecutionContext(
                id,
                def != null ? def.getDescription() : null,
                state,
                null
        );
        try {
            RuleExecutionContext.bind(ctx);
            boolean applicable = delegate.isApplicable(state, ctx);
            if (!applicable) {
                String skippedMessage = "[SKIPPED] Условия правила " + id + " не выполнены для текущего состояния склада";
                auditService.record(toAuditEntry("SKIPPED", ctx, Collections.emptySet(), skippedMessage));
            }
            return applicable;
        } catch (RuntimeException ex) {
            auditService.record(toAuditEntry("FAILED", ctx, Collections.emptySet(),
                    "Ошибка проверки применимости правила: " + ex.getMessage()));
            throw ex;
        } finally {
            RuleExecutionContext.clear();
        }
    }

    @Override
    public Set<String> execute(WarehouseState state) {
        RuleDefinition def = getDefinition();
        RuleExecutionContext ctx = new RuleExecutionContext(
                id,
                def != null ? def.getDescription() : null,
                state,
                null
        );
        try {
            RuleExecutionContext.bind(ctx);
            Set<String> produced = delegate.execute(state, ctx);
            auditService.record(toAuditEntry("APPLIED", ctx, produced, null));
            return produced;
        } catch (RuntimeException ex) {
            auditService.record(toAuditEntry("FAILED", ctx, Collections.emptySet(),
                    "Ошибка выполнения правила: " + ex.getMessage()));
            throw ex;
        } finally {
            RuleExecutionContext.clear();
        }
    }

    private AuditEntry toAuditEntry(String decision, RuleExecutionContext ctx, Set<String> events, String overrideMessage) {
        AuditEntry entry = new AuditEntry();
        RuleDefinition def = getDefinition();
        entry.setRuleId(id);
        entry.setDecision(decision);
        entry.setTriggeringEvent(ctx.getTriggeringEvent());
        entry.setPriority(def != null ? def.getPriority() : 0);
        entry.setEvaluatedConditions(ctx.getEvaluatedConditions());
        entry.setFacts(ctx.getFacts());
        entry.setProducedEvents(events == null ? List.of() : new ArrayList<>(events));

        String explanation = overrideMessage;
        if (explanation == null || explanation.isBlank()) {
            if (!ctx.getMessages().isEmpty()) {
                explanation = String.join(" ", ctx.getMessages());
            } else if (def != null && def.getDescription() != null && !def.getDescription().isBlank()) {
                explanation = def.getDescription();
            } else {
                explanation = "Правило " + id + " обработано без дополнительного пояснения.";
            }
        }
        entry.setExplanation(explanation);
        return entry;
    }
}
