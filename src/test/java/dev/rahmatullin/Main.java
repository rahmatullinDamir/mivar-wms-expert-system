package dev.rahmatullin;

import dev.rahmatullin.domain.WarehouseState;
import dev.rahmatullin.engine.MivarGraphEngine;
import dev.rahmatullin.engine.MockDataLoader;

import java.nio.file.Path;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        MockDataLoader dataLoader = new MockDataLoader();
        WarehouseState state = dataLoader.loadState(Path.of("src/test/resources/mock_state.json"));

        System.out.println("Warehouse load successfully.");
        System.out.println("Robots available: " + state.getRobots().size());
        System.out.println("Order status before: " + state.getOrders().getFirst().getStatus());

        MivarGraphEngine engine = new MivarGraphEngine();

        // Use new rule registrar and loader to register JSON-driven rules with audit
        dev.rahmatullin.audit.InMemoryAuditService auditService = new dev.rahmatullin.audit.InMemoryAuditService();
        dev.rahmatullin.rules.RuleRegistrar registrar = new dev.rahmatullin.rules.RuleRegistrar(engine, auditService);
        dev.rahmatullin.rules.RuleLoader ruleLoader = new dev.rahmatullin.rules.RuleLoader(Path.of("src/main/resources/config/rules"), registrar);
        try {
            ruleLoader.loadAll();
            ruleLoader.startWatcher();
        } catch (Exception e) {
            System.err.println("Failed to load rules: " + e.getMessage());
        }

        // register legacy test rule as well
        engine.registerRule(new TestRule());

        engine.fireEvents(state, Set.of("SYSTEM_STARTUP", "NEW_ORDER"));

        System.out.println("Order status after: " + state.getOrders().getFirst().getStatus());
        System.out.println("Robot status after: " + state.getRobots().getFirst().getStatus());

        // print audit entries for the run (no traceId used here; show all)
        System.out.println("Audit entries:\n");
        auditService.all().forEach(a ->
                System.out.println(a.getRuleId() + " => " + a.getDecision() + " | " + a.getExplanation()));
    }
}