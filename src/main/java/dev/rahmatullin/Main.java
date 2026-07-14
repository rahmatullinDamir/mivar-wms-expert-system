package dev.rahmatullin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.rahmatullin.audit.AuditEntry;
import dev.rahmatullin.audit.InMemoryAuditService;
import dev.rahmatullin.domain.*;
import dev.rahmatullin.engine.MivarGraphEngine;
import dev.rahmatullin.rules.RuleDefinition;
import dev.rahmatullin.rules.RuleLoader;
import dev.rahmatullin.rules.RuleRegistrar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Класс для теста работы системы с моками
public class Main {

    public static void main(String[] args) {
        System.out.println("=== ЗАПУСК МИВАРНОЙ ЭКСПЕРТНОЙ СИСТЕМЫ ===");

        try {
            // 1. Создаем временную директорию для правил
            Path tempRulesDir = Files.createTempDirectory("mivar_rules_");
            System.out.println("Временная папка для правил: " + tempRulesDir.toAbsolutePath());

            // Записываем JSON-правила во временную папку
            writeDemoRules(tempRulesDir);

            // 2. Инициализируем компоненты системы
            MivarGraphEngine engine = new MivarGraphEngine();
            InMemoryAuditService auditService = new InMemoryAuditService();
            RuleRegistrar registrar = new RuleRegistrar(engine, auditService);
            RuleLoader ruleLoader = new RuleLoader(tempRulesDir, registrar);

            // Загружаем правила из папки
            ruleLoader.loadAll();
            // Запускаем watcher для hot-reload (на случай, если захотите изменить JSON во время работы)
            ruleLoader.startWatcher();

            // 3. Создаем моковое состояние склада
            WarehouseState warehouseState = createMockWarehouseState();

            System.out.println("\n--- НАЧАЛЬНОЕ СОСТОЯНИЕ СКЛАДА ---");
            printWarehouseState(warehouseState);

            // 4. Запускаем логический вывод
            // Имитируем старт системы (событие SYSTEM_STARTUP)
            System.out.println("\n--- ЗАПУСК ДВИЖКА (SYSTEM_STARTUP) ---");
            engine.fireEvents(warehouseState, Set.of("SYSTEM_STARTUP"));

            System.out.println("\n--- КОНЕЧНОЕ СОСТОЯНИЕ СКЛАДА ---");
            printWarehouseState(warehouseState);

            // 5. Выводим аудит принятых решений
            System.out.println("\n--- АУДИТ ПРИНЯТЫХ РЕШЕНИЙ ---");
            List<AuditEntry> auditEntries = auditService.all();
            if (auditEntries.isEmpty()) {
                System.out.println("Аудит пуст. Ни одно правило не сработало.");
            } else {
                for (AuditEntry entry : auditEntries) {
                    if ("APPLIED".equals(entry.getDecision())) {
                        System.out.printf("[%s] Правило: %s (Приоритет: %d)%n",
                                entry.getDecision(), entry.getRuleId(), entry.getPriority());
                        System.out.printf("   Пояснение: %s%n", entry.getExplanation());
                        if (entry.getProducedEvents() != null && !entry.getProducedEvents().isEmpty()) {
                            System.out.printf("   Сгенерированные события: %s%n", entry.getProducedEvents());
                        }
                        System.out.println();
                    } else if ("FAILED".equals(entry.getDecision())) {
                        System.err.printf("[ОШИБКА] Правило %s упало: %s%n", entry.getRuleId(), entry.getExplanation());
                    }
                }
            }

            // Корректно завершаем работу
            System.out.println("Тестирование успешно завершено.");
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static WarehouseState createMockWarehouseState() {
        WarehouseState state = new WarehouseState();

        // Зоны склада
        Zone zoneA = Zone.builder().id("zone-A").center(new Point(10, 10)).build();
        Zone zoneB = Zone.builder().id("zone-B").center(new Point(50, 50)).build();
        state.setZones(List.of(zoneA, zoneB));

        // Роботы (один сломан, один слабенький близко, один мощный далеко)
        Robot robot1 = Robot.builder().id("robot-broken").liftingCapacity(50).currentZoneId("zone-A").status(RobotStatus.BROKEN).position(new Point(10, 12)).build();
        Robot robot2 = Robot.builder().id("robot-light").liftingCapacity(15).currentZoneId("zone-A").status(RobotStatus.FREE).position(new Point(12, 10)).build();
        Robot robot3 = Robot.builder().id("robot-heavy").liftingCapacity(100).currentZoneId("zone-B").status(RobotStatus.FREE).position(new Point(50, 52)).build();
        state.setRobots(new ArrayList<>(List.of(robot1, robot2, robot3)));

        // Товар на полках
        Item lightItem = Item.builder().id("item-light").weight(5.0).shelfId("shelf-1").build();
        Item heavyItem = Item.builder().id("item-heavy").weight(35.0).shelfId("shelf-2").build();
        state.setItems(new ArrayList<>(List.of(lightItem, heavyItem)));

        Shelf shelf1 = Shelf.builder().id("shelf-1").fullness(50).currentZoneId("zone-A").position(new Point(15, 15)).build();
        Shelf shelf2 = Shelf.builder().id("shelf-2").fullness(80).currentZoneId("zone-B").position(new Point(45, 45)).build();
        state.setShelves(new ArrayList<>(List.of(shelf1, shelf2)));

        // Заказы (один срочный, один обычный тяжелый)
        Order order1 = Order.builder().id("order-urgent").itemId("item-light").destinationZoneId("zone-A").isUrgent(true).status(OrderStatus.PENDING).build();
        Order order2 = Order.builder().id("order-heavy").itemId("item-heavy").destinationZoneId("zone-B").isUrgent(false).status(OrderStatus.PENDING).build();
        state.setOrders(new ArrayList<>(List.of(order1, order2)));

        return state;
    }

    private static void printWarehouseState(WarehouseState state) {
        System.out.println("  Роботы:");
        for (Robot r : state.getRobots()) {
            System.out.printf("    - ID: %s, Статус: %s, Грузоподъемность: %d кг%n", r.getId(), r.getStatus(), r.getLiftingCapacity());
        }
        System.out.println("  Заказы:");
        for (Order o : state.getOrders()) {
            System.out.printf("    - ID: %s, Статус: %s, Срочный: %s, Товар: %s%n", o.getId(), o.getStatus(), o.isUrgent(), o.getItemId());
        }
    }

    private static void writeDemoRules(Path rulesDir) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Правило аварийного восстановления
        RuleDefinition brokenRecovery = new RuleDefinition();
        brokenRecovery.setId("broken-robot-recovery-1");
        brokenRecovery.setDescription("Сбой робота: перепланировать и переназначить ожидающий заказ на свободного робота");
        brokenRecovery.setPriority(90);
        brokenRecovery.setTriggers(List.of("ROBOT_STATUS_CHANGED", "SYSTEM_STARTUP"));
        brokenRecovery.setActive(true);
        brokenRecovery.setConditions(List.of(
                "f.hasBrokenRobots(state)",
                "f.hasPendingOrders(state)",
                "f.hasFreeRobots(state)"
        ));
        brokenRecovery.setActions(List.of(
                "f.recoverFromBrokenRobot(state)",
                "f.explain('Выполнено аварийное перепланирование после сбоя робота')"
        ));
        brokenRecovery.setProducedEvents(List.of("ORDER_REASSIGNED_AFTER_FAILURE"));

        // 2. Срочный заказ
        RuleDefinition urgentPreempt = new RuleDefinition();
        urgentPreempt.setId("urgent-preempt-1");
        urgentPreempt.setDescription("Срочный заказ: назначить лучшего свободного робота с учетом грузоподъемности и расстояния");
        urgentPreempt.setPriority(100);
        urgentPreempt.setTriggers(List.of("NEW_ORDER", "SYSTEM_STARTUP"));
        urgentPreempt.setActive(true);
        urgentPreempt.setConditions(List.of(
                "f.hasUrgentPendingOrders(state)",
                "f.hasFreeRobots(state)"
        ));
        urgentPreempt.setActions(List.of(
                "f.assignBestRobotToUrgentOrder(state)",
                "f.explain('Срочный заказ обработан по приоритетному правилу urgent-preempt-1')"
        ));
        urgentPreempt.setProducedEvents(List.of("ORDER_ASSIGNED", "URGENT_ORDER_ASSIGNED"));

        // 3. Тяжелый заказ
        RuleDefinition heavyAssignment = new RuleDefinition();
        heavyAssignment.setId("heavy-item-assignment-1");
        heavyAssignment.setDescription("Тяжелый заказ: назначить только робота с достаточной грузоподъемностью");
        heavyAssignment.setPriority(80);
        heavyAssignment.setTriggers(List.of("NEW_ORDER", "SYSTEM_STARTUP", "ROBOT_STATUS_CHANGED"));
        heavyAssignment.setActive(true);
        heavyAssignment.setConditions(List.of(
                "f.hasPendingOrders(state)",
                "f.hasFreeRobots(state)"
        ));
        heavyAssignment.setActions(List.of(
                "f.assignHeavyOrderToCapableRobot(state, 20.0)",
                "f.explain('Проверка тяжелых заказов (вес >= 20) выполнена')"
        ));
        heavyAssignment.setProducedEvents(List.of("HEAVY_ORDER_ASSIGNED"));

        // Записываем файлы
        mapper.writeValue(rulesDir.resolve("broken-robot-recovery-1.json").toFile(), brokenRecovery);
        mapper.writeValue(rulesDir.resolve("urgent-preempt-1.json").toFile(), urgentPreempt);
        mapper.writeValue(rulesDir.resolve("heavy-item-assignment-1.json").toFile(), heavyAssignment);
    }
}