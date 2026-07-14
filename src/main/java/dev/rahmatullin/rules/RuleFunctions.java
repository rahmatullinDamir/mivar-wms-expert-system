package dev.rahmatullin.rules;

import dev.rahmatullin.domain.Robot;
import dev.rahmatullin.domain.RobotStatus;
import dev.rahmatullin.domain.Order;
import dev.rahmatullin.domain.OrderStatus;
import dev.rahmatullin.domain.Item;
import dev.rahmatullin.domain.Zone;
import dev.rahmatullin.domain.WarehouseState;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;

/**
 * Minimal helper functions exposed to JEXL expressions as 'f'.
 * Expandable with domain-specific utilities (distance, capacity checks, etc.).
 */
public class RuleFunctions {
    public boolean hasPendingOrders(WarehouseState state) {
        return state.getOrders() != null && state.getOrders().stream().anyMatch(o -> o.getStatus() == OrderStatus.PENDING);
    }

    public boolean hasFreeRobots(WarehouseState state) {
        return state.getRobots() != null && state.getRobots().stream().anyMatch(r -> r.getStatus() == RobotStatus.FREE);
    }

    public boolean hasUrgentPendingOrders(WarehouseState state) {
        return state.getOrders() != null && state.getOrders().stream()
                .anyMatch(o -> o.getStatus() == OrderStatus.PENDING && o.isUrgent());
    }

    public String firstUrgentPendingOrderId(WarehouseState state) {
        if (state.getOrders() == null) {
            return null;
        }
        return state.getOrders().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING && o.isUrgent())
                .map(Order::getId)
                .findFirst()
                .orElse(null);
    }

    public boolean hasBrokenRobots(WarehouseState state) {
        return state.getRobots() != null && state.getRobots().stream().anyMatch(r -> r.getStatus() == RobotStatus.BROKEN);
    }

    public Optional<Robot> findFirstFreeRobot(WarehouseState state) {
        List<Robot> robots = state.getRobots();
        if (robots == null || robots.isEmpty()) {
            return Optional.empty();
        }
        return robots.stream().filter(r -> r.getStatus() == RobotStatus.FREE).findFirst();
    }

    public String firstFreeRobotId(WarehouseState state) {
        return findFirstFreeRobot(state).map(Robot::getId).orElse(null);
    }

    public String firstPendingOrderId(WarehouseState state) {
        if (state.getOrders() == null) {
            return null;
        }
        return state.getOrders().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .map(Order::getId)
                .findFirst()
                .orElse(null);
    }

    public boolean hasCapableFreeRobotForOrder(WarehouseState state, String orderId) {
        Order order = findOrderById(state, orderId);
        double weight = getOrderWeight(state, order);
        return state.getRobots() != null && state.getRobots().stream()
                .anyMatch(r -> r.getStatus() == RobotStatus.FREE && r.getLiftingCapacity() >= weight);
    }

    public String bestFreeRobotIdForOrder(WarehouseState state, String orderId) {
        Order order = findOrderById(state, orderId);
        double weight = getOrderWeight(state, order);
        Zone destination = findZoneById(state, order.getDestinationZoneId());
        if (state.getRobots() == null || state.getRobots().isEmpty()) {
            return null;
        }

        return state.getRobots().stream()
                .filter(r -> r.getStatus() == RobotStatus.FREE)
                .filter(r -> r.getLiftingCapacity() >= weight)
                .min(Comparator.comparingDouble(r -> robotScoreForOrder(r, order, destination, weight)))
                .map(Robot::getId)
                .orElse(null);
    }

    public boolean assignBestRobotToUrgentOrder(WarehouseState state) {
        String orderId = firstUrgentPendingOrderId(state);
        if (orderId == null) {
            return false;
        }
        String robotId = bestFreeRobotIdForOrder(state, orderId);
        if (robotId == null) {
            return false;
        }
        return assignRobotToOrder(state, robotId, orderId);
    }

    public boolean assignBestRobotToFirstPendingOrder(WarehouseState state) {
        String orderId = firstPendingOrderId(state);
        if (orderId == null) {
            return false;
        }
        String robotId = bestFreeRobotIdForOrder(state, orderId);
        if (robotId == null) {
            return false;
        }
        return assignRobotToOrder(state, robotId, orderId);
    }

    public boolean assignHeavyOrderToCapableRobot(WarehouseState state, double minWeight) {
        if (state.getOrders() == null) {
            return false;
        }
        Optional<Order> heavyOrder = state.getOrders().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .filter(o -> getOrderWeight(state, o) >= minWeight)
                .findFirst();
        if (heavyOrder.isEmpty()) {
            return false;
        }
        String orderId = heavyOrder.get().getId();
        String robotId = bestFreeRobotIdForOrder(state, orderId);
        if (robotId == null) {
            return false;
        }
        RuleExecutionContext.current().ifPresent(ctx ->
                ctx.addMessage(String.format(
                        "Тяжелый заказ %s (вес %.1f) назначается только на робота с достаточной грузоподъемностью.",
                        orderId, getOrderWeight(state, heavyOrder.get())
                ))
        );
        return assignRobotToOrder(state, robotId, orderId);
    }

    public boolean recoverFromBrokenRobot(WarehouseState state) {
        if (!hasBrokenRobots(state) || !hasPendingOrders(state) || !hasFreeRobots(state)) {
            return false;
        }
        boolean assigned = assignBestRobotToFirstPendingOrder(state);
        if (assigned) {
            RuleExecutionContext.current().ifPresent(ctx ->
                    ctx.addMessage("Обнаружен сбой робота: заказ переназначен на свободного робота.")
            );
        }
        return assigned;
    }

    public boolean isRobotInOrderDestinationZone(WarehouseState state, String robotId, String orderId) {
        Robot robot = findRobotById(state, robotId);
        Order order = findOrderById(state, orderId);
        return robot.getCurrentZoneId() != null && robot.getCurrentZoneId().equals(order.getDestinationZoneId());
    }

    public boolean assignRobotToOrder(WarehouseState state, String robotId, String orderId) {
        if (robotId == null || orderId == null) {
            throw new IllegalArgumentException("robotId/orderId must not be null");
        }
        Robot robot = findRobotById(state, robotId);
        Order order = findOrderById(state, orderId);
        double orderWeight = getOrderWeight(state, order);
        if (robot.getLiftingCapacity() < orderWeight) {
            throw new IllegalStateException(String.format(
                    "Robot %s capacity %s is not enough for order %s weight %.2f",
                    robotId, robot.getLiftingCapacity(), orderId, orderWeight
            ));
        }

        robot.setStatus(RobotStatus.BUSY);
        order.setStatus(OrderStatus.PROCESSING);

        String explanation = String.format(
                "Робот %s назначен на заказ %s: грузоподъемность %d >= вес %.1f, робот переведен в BUSY, заказ в PROCESSING.",
                robotId, orderId, robot.getLiftingCapacity(), orderWeight
        );
        RuleExecutionContext.current().ifPresent(ctx -> {
            ctx.setFact("robotId", robotId);
            ctx.setFact("orderId", orderId);
            ctx.setFact("orderWeight", orderWeight);
            ctx.addMessage(explanation);
            ctx.emitEvent("ROBOT_STATUS_CHANGED");
            ctx.emitEvent("ORDER_STATUS_CHANGED");
        });
        return true;
    }

    public boolean emit(String event) {
        RuleExecutionContext.current().ifPresent(ctx -> ctx.emitEvent(event));
        return true;
    }

    public String explain(String message) {
        RuleExecutionContext.current().ifPresent(ctx -> ctx.addMessage(message));
        return message;
    }

    public double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx*dx + dy*dy);
    }

    private Robot findRobotById(WarehouseState state, String robotId) {
        if (state.getRobots() == null) {
            throw new IllegalArgumentException("Robots list is not initialized");
        }
        return state.getRobots().stream()
                .filter(r -> robotId.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Robot not found: " + robotId));
    }

    private Order findOrderById(WarehouseState state, String orderId) {
        if (state.getOrders() == null) {
            throw new IllegalArgumentException("Orders list is not initialized");
        }
        return state.getOrders().stream()
                .filter(o -> orderId.equals(o.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private Zone findZoneById(WarehouseState state, String zoneId) {
        if (zoneId == null || state.getZones() == null) {
            return null;
        }
        return state.getZones().stream()
                .filter(z -> zoneId.equals(z.getId()))
                .findFirst()
                .orElse(null);
    }

    private double getOrderWeight(WarehouseState state, Order order) {
        if (order.getItemId() == null || state.getItems() == null) {
            return 0.0;
        }
        return state.getItems().stream()
                .filter(i -> order.getItemId().equals(i.getId()))
                .map(Item::getWeight)
                .findFirst()
                .orElse(0.0);
    }

    private double robotScoreForOrder(Robot robot, Order order, Zone destination, double weight) {
        double distancePenalty = 1000.0;
        if (robot.getPosition() != null && destination != null && destination.getCenter() != null) {
            distancePenalty = distance(
                    robot.getPosition().x(), robot.getPosition().y(),
                    destination.getCenter().x(), destination.getCenter().y()
            );
        }
        double zoneBonus = (robot.getCurrentZoneId() != null && robot.getCurrentZoneId().equals(order.getDestinationZoneId()))
                ? -100.0 : 0.0;
        double capacityReservePenalty = robot.getLiftingCapacity() - weight;
        return distancePenalty + capacityReservePenalty + zoneBonus;
    }
}
