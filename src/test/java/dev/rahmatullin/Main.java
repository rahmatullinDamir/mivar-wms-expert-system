package dev.rahmatullin;

import dev.rahmatullin.domain.WarehouseState;
import dev.rahmatullin.engine.MivarGraphEngine;
import dev.rahmatullin.engine.MockDataLoader;

import java.nio.file.Path;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        MockDataLoader loader = new MockDataLoader();
        WarehouseState state = loader.loadState(Path.of("src/test/resources/mock_state.json"));

        System.out.println("Warehouse load successfully.");
        System.out.println("Robots available: " + state.getRobots().size());
        System.out.println("Order status before: " + state.getOrders().getFirst().getStatus());

        MivarGraphEngine engine = new MivarGraphEngine();

        engine.registerRule(new TestRule());

        engine.fireEvents(state, Set.of("SYSTEM_STARTUP"));

        System.out.println("Order status after: " + state.getOrders().getFirst().getStatus());
        System.out.println("Robot status after: " + state.getRobots().getFirst().getStatus());
    }
}