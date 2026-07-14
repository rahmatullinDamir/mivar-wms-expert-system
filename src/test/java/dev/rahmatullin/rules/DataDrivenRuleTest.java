package dev.rahmatullin.rules;

import dev.rahmatullin.domain.RobotStatus;
import dev.rahmatullin.domain.WarehouseState;
import dev.rahmatullin.engine.MockDataLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataDrivenRuleTest {

    @Test
    void shouldStoreConditionResultsInContext() {
        WarehouseState state = new MockDataLoader().loadState(Path.of("src", "test", "resources", "mock_state.json"));
        RuleDefinition def = new RuleDefinition();
        def.setId("condition-audit-test");
        def.setActive(true);
        def.setTriggers(List.of("SYSTEM_STARTUP"));
        def.setConditions(List.of(
                "f.hasPendingOrders(state)",
                "f.hasFreeRobots(state)"
        ));
        def.setActions(List.of("f.explain('ok')"));

        DataDrivenRule rule = new DataDrivenRule(def);
        RuleExecutionContext ctx = new RuleExecutionContext(def.getId(), def.getDescription(), state, null);

        boolean applicable = rule.isApplicable(state, ctx);

        assertTrue(applicable);
        assertEquals(2, ctx.getEvaluatedConditions().size());
        assertEquals(true, ctx.getEvaluatedConditions().get(0).get("result"));
        assertEquals(true, ctx.getEvaluatedConditions().get(1).get("result"));
    }

    @Test
    void shouldNotEmitStaticEventsWhenBusinessActionFailed() {
        WarehouseState state = new MockDataLoader().loadState(Path.of("src", "test", "resources", "mock_state.json"));
        state.getRobots().getFirst().setStatus(RobotStatus.BUSY);

        RuleDefinition def = new RuleDefinition();
        def.setId("static-events-guard-test");
        def.setActive(true);
        def.setTriggers(List.of("NEW_ORDER"));
        def.setActions(List.of(
                "f.assignBestRobotToFirstPendingOrder(state)",
                "f.explain('не удалось назначить робота')"
        ));
        def.setProducedEvents(List.of("ORDER_ASSIGNED"));

        DataDrivenRule rule = new DataDrivenRule(def);
        RuleExecutionContext ctx = new RuleExecutionContext(def.getId(), def.getDescription(), state, null);

        Set<String> events = rule.execute(state, ctx);

        assertTrue(events.isEmpty());
        assertFalse(ctx.getProducedEvents().contains("ORDER_ASSIGNED"));
    }
}
