package dev.rahmatullin;

import dev.rahmatullin.domain.*;
import dev.rahmatullin.engine.MivarRule;

import java.util.Optional;
import java.util.Set;

public class TestRule implements MivarRule {
    @Override
    public Set<String> getTriggers() {
        return Set.of("SYSTEM_STARTUP", "NEW_ORDER");
    }

    @Override
    public boolean isApplicable(WarehouseState state) {
        boolean hasPendingOrder = state.getOrders().stream().anyMatch(o -> o.getStatus() == OrderStatus.PENDING);
        boolean hasFreeRobot = state.getRobots().stream().anyMatch(r -> r.getStatus() == RobotStatus.FREE);

        return hasPendingOrder && hasFreeRobot;
    }

    @Override
    public Set<String> execute(WarehouseState state) {
        Optional<Order> orderOpt = state.getOrders().stream().filter(o -> o.getStatus() == OrderStatus.PENDING).findFirst();
        Optional<Robot> robotOpt = state.getRobots().stream().filter(r -> r.getStatus() == RobotStatus.FREE).findFirst();

        if (orderOpt.isPresent() && robotOpt.isPresent()) {
            Order order = orderOpt.get();
            Robot robot = robotOpt.get();

            order.setStatus(OrderStatus.PROCESSING);
            robot.setStatus(RobotStatus.BUSY);

            System.out.println("BUSINESS-LOGIC: ROBOT " + robot.getId() + " assigned to perform warehouse task: " + order.getId() + " Detail: " + order.getItemId());

            return Set.of("ORDER_STATUS_CHANGED", "ROBOT_STATUS_CHANGED");
        }

        return Set.of();
    }
}
