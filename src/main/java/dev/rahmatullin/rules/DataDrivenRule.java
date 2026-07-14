package dev.rahmatullin.rules;

import dev.rahmatullin.domain.WarehouseState;
import dev.rahmatullin.engine.MivarRule;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlSandbox;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Rule implementation backed by a JSON RuleDefinition. Uses JEXL to evaluate conditions and simple actions.
 */
public class DataDrivenRule implements MivarRule {
    private volatile RuleDefinition def;
    private final JexlEngine jexl;
    private final RuleFunctions functions = new RuleFunctions();

    public DataDrivenRule(RuleDefinition def) {
        this.def = def;
        this.jexl = new JexlBuilder()
                .strict(true)
                .silent(false)
                .sandbox(buildSandbox())
                .create();
    }

    public void setDefinition(RuleDefinition def) {
        this.def = def;
    }

    public RuleDefinition getDefinition() { return this.def; }

    @Override
    public Set<String> getTriggers() {
        if (def == null || !def.isActive() || def.getTriggers() == null) return Collections.emptySet();
        return def.getTriggers().stream().collect(Collectors.toSet());
    }

    @Override
    public boolean isApplicable(WarehouseState state) {
        RuleDefinition current = this.def;
        RuleExecutionContext ctx = new RuleExecutionContext(
                current != null ? current.getId() : null,
                current != null ? current.getDescription() : null,
                state,
                null
        );
        return isApplicable(state, ctx);
    }

    public boolean isApplicable(WarehouseState state, RuleExecutionContext ctx) {
        if (def == null || !def.isActive()) return false;
        List<String> conds = def.getConditions();
        if (conds == null || conds.isEmpty()) return true;

        MapContext mapContext = buildMapContext(state);
        for (String expr : conds) {
            JexlExpression e = jexl.createExpression(expr);
            Object res = e.evaluate(mapContext);
            boolean passed = (res instanceof Boolean) && (Boolean) res;
            ctx.addConditionResult(expr, passed);
            if (!passed) {
                return false;
            }
        }
        ctx.addMessage("Все условия правила выполнены.");
        return true;
    }

    @Override
    public Set<String> execute(WarehouseState state) {
        RuleDefinition current = this.def;
        RuleExecutionContext ctx = new RuleExecutionContext(
                current != null ? current.getId() : null,
                current != null ? current.getDescription() : null,
                state,
                null
        );
        return execute(state, ctx);
    }

    public Set<String> execute(WarehouseState state, RuleExecutionContext ctx) {
        if (def == null || !def.isActive()) return Collections.emptySet();

        if (def.getDescription() != null && !def.getDescription().isBlank()) {
            ctx.addMessage(def.getDescription());
        }

        MapContext mapContext = buildMapContext(state);
        List<String> actions = def.getActions();
        boolean actionSucceeded = false;
        if (actions != null) {
            for (String action : actions) {
                if (action == null || action.isBlank()) {
                    continue;
                }
                JexlScript script = jexl.createScript(action);
                Object result = script.execute(mapContext);
                if (result instanceof Boolean) {
                    actionSucceeded = actionSucceeded || (Boolean) result;
                }
                if (result instanceof String && !((String) result).isBlank()) {
                    ctx.addMessage((String) result);
                } else if (result instanceof Iterable<?>) {
                    for (Object item : (Iterable<?>) result) {
                        if (item instanceof String) {
                            ctx.emitEvent((String) item);
                        }
                    }
                }
            }
        }

        List<String> staticEvents = def.getProducedEvents();
        boolean emitStaticEvents = (actions == null || actions.isEmpty()) || actionSucceeded;
        if (emitStaticEvents && staticEvents != null) {
            for (String event : staticEvents) {
                ctx.emitEvent(event);
            }
        }

        return new LinkedHashSet<>(ctx.getProducedEvents());
    }

    private MapContext buildMapContext(WarehouseState state) {
        MapContext ctx = new MapContext();
        ctx.set("state", state);
        ctx.set("f", functions);
        return ctx;
    }

    private JexlSandbox buildSandbox() {
        // Block-by-default sandbox: only explicit helper methods are callable from rule scripts.
        JexlSandbox sandbox = new JexlSandbox(false, true);
        JexlSandbox.Permissions helperPermissions = sandbox.allow(RuleFunctions.class.getName());
        helperPermissions.execute().add("hasPendingOrders");
        helperPermissions.execute().add("hasFreeRobots");
        helperPermissions.execute().add("hasUrgentPendingOrders");
        helperPermissions.execute().add("hasBrokenRobots");
        helperPermissions.execute().add("findFirstFreeRobot");
        helperPermissions.execute().add("firstFreeRobotId");
        helperPermissions.execute().add("firstPendingOrderId");
        helperPermissions.execute().add("firstUrgentPendingOrderId");
        helperPermissions.execute().add("hasCapableFreeRobotForOrder");
        helperPermissions.execute().add("bestFreeRobotIdForOrder");
        helperPermissions.execute().add("assignBestRobotToUrgentOrder");
        helperPermissions.execute().add("assignBestRobotToFirstPendingOrder");
        helperPermissions.execute().add("assignHeavyOrderToCapableRobot");
        helperPermissions.execute().add("recoverFromBrokenRobot");
        helperPermissions.execute().add("isRobotInOrderDestinationZone");
        helperPermissions.execute().add("assignRobotToOrder");
        helperPermissions.execute().add("emit");
        helperPermissions.execute().add("explain");
        helperPermissions.execute().add("distance");
        return sandbox;
    }
}
