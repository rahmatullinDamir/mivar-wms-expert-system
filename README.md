# Mivar WMS Expert System

**Экспертная система для автоматического назначения роботов на заказы в складской системе (WMS).**

Система использует продукционную модель (Mivar-граф) для принятия решений о распределении заказов между складами на основе JSON-определяемых правил с поддержкой hot-reload и полного аудита.

---

## Содержание

- [Архитектура](#архитектура)
- [Доменная модель](#доменная-модель)
- [Движок правил (MivarGraphEngine)](#движок-правил-mivargraphengine)
- [Формат правил (JSON)](#формат-правил-json)
- [Встроенные функции (RuleFunctions)](#встроенные-функции-rulefunctions)
- [Аудит](#аудит)
- [Загрузка правил и Hot-Reload](#загрузка-правил-и-hot-reload)
- [Кейсы использования](#кейсы-использования)
- [Запуск](#запуск)

---

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                      Main (Entry Point)                 │
├─────────────────────────────────────────────────────────┤
│  MivarGraphEngine                                       │
│  ├── RuleGraph (Map<Trigger, List<MivarRule>>)          │
│  └── fireEvents(state, initialEvents)                   │
│       └── BFS-очередь событий с детекцией циклов        │
├─────────────────────────────────────────────────────────┤
│  RuleLoader                                             │
│  ├── loadAll() — загрузка *.json из директории          │
│  └── WatchService — hot-reload при изменении файлов     │
├─────────────────────────────────────────────────────────┤
│  RuleRegistrar                                          │
│  └── registry: Map<RuleId, AuditedRuleWrapper>          │
├─────────────────────────────────────────────────────────┤
│  AuditedRuleWrapper (decorator)                         │
│  └── DataDrivenRule (JEXL-движок)                       │
├─────────────────────────────────────────────────────────┤
│  AuditService / InMemoryAuditService                    │
│  └── AuditEntry (traceId, ruleId, decision, facts...)   │
├─────────────────────────────────────────────────────────┤
│  RuleFunctions (f) — helper-методы для JEXL-скриптов    │
├─────────────────────────────────────────────────────────┤
│  Domain (WarehouseState, Robot, Order, Item, Shelf...)  │
└─────────────────────────────────────────────────────────┘
```

### Ключевые технологии

| Компонент       | Технология                          |
|-----------------|-------------------------------------|
| Язык            | Java 21                             |
| Сборка          | Gradle                              |
| JSON-серилизация| Jackson Databind 2.18.2             |
| Expression Engine| Apache Commons JEXL 3.2 (sandbox)  |
| Логирование     | SLF4J + Logback                     |
| Тестирование    | JUnit 5 (Jupiter 6)                 |
| Lombok          | 1.18.36                             |

---

## Доменная модель

### WarehouseState
Корневой объект состояния склада — единый источник истины для всех правил.

```
WarehouseState
├── List<Robot> robots
├── List<Order> orders
├── List<Shelf> shelves
├── List<Item> items
└── List<Zone> zones
```

### Robot
| Поле                | Тип      | Описание                        |
|---------------------|----------|---------------------------------|
| `id`                | String   | Уникальный идентификатор        |
| `liftingCapacity`   | int      | Максимальная грузоподъёмность (кг) |
| `currentZoneId`     | String   | ID текущей зоны                 |
| `status`            | RobotStatus | FREE / BUSY / BROKEN         |
| `position`          | Point    | Координаты (x, y)               |

### Order
| Поле                | Тип         | Описание                        |
|---------------------|-------------|---------------------------------|
| `id`                | String      | Уникальный идентификатор        |
| `parentOrderId`     | String      | ID родительского заказа         |
| `itemId`            | String      | ID товара в заказе              |
| `destinationZoneId` | String      | Целевая зона                    |
| `isUrgent`          | boolean     | Флаг срочности                  |
| `status`            | OrderStatus | PENDING / PROCESSING / COMPLETED / CANCELED |

### Item
| Поле       | Тип   | Описание              |
|------------|-------|-----------------------|
| `id`       | String| Уникальный ID         |
| `weight`   | double| Вес товара (кг)       |
| `shelfId`  | String| ID полки хранения     |

### Shelf
| Поле           | Тип    | Описание              |
|----------------|--------|-----------------------|
| `id`           | String | Уникальный ID         |
| `fullness`     | int    | Заполненность (%)     |
| `currentZoneId`| String | ID зоны               |
| `position`     | Point  | Координаты (x, y)     |

### Zone
| Поле   | Тип   | Описание              |
|--------|-------|-----------------------|
| `id`   | String| Уникальный ID         |
| `center`| Point| Центр зоны (x, y)    |

### Point
```java
public record Point(int x, int y) {}
```

---

## Движок правил (MivarGraphEngine)

### Принцип работы

Движок реализует **продукционную систему с графом событий**:

1. **Регистрация правил** — каждое правило привязывается к одному или нескольким триггерам (событиям).
2. **Fire Events** — при поступлении начальных событий запускается BFS-очередь.
3. **Поиск зависимых правил** — для каждого события находятся все правила, подписанные на это событие.
4. **Проверка применимости** — через `isApplicable(state)` проверяются условия правила.
5. **Выполнение** — через `execute(state)` выполняются действия, которые могут генерировать новые события.
6. **Цепная реакция** — новые события добавляются в очередь и обрабатываются рекурсивно.
7. **Детекция циклов** — лимит в 10 000 итераций защищает от бесконечных циклов.

### Жизненный цикл правила

```
Регистрация → Подписка на триггеры → Проверка условий (isApplicable)
    → Выполнение действий (execute) → Генерация новых событий → Повторный проход
```

### Интерфейс MivarRule

```java
public interface MivarRule {
    // Триггеры: события, при которых правило должно сработать
    Set<String> getTriggers();

    // Проверка условий (ЕСЛИ)
    boolean isApplicable(WarehouseState state);

    // Выполнение действий (ТО) + возврат новых событий
    Set<String> execute(WarehouseState state);
}
```

---

## Формат правил (JSON)

Правила определяются в JSON-файлах. Пример:

```json
{
  "id": "urgent-preempt-1",
  "description": "Срочный заказ: назначить лучшего свободного робота",
  "priority": 100,
  "triggers": ["NEW_ORDER", "SYSTEM_STARTUP"],
  "active": true,
  "conditions": [
    "f.hasUrgentPendingOrders(state)",
    "f.hasFreeRobots(state)"
  ],
  "actions": [
    "f.assignBestRobotToUrgentOrder(state)",
    "f.explain('Срочный заказ обработан по приоритетному правилу')"
  ],
  "producedEvents": ["ORDER_ASSIGNED", "URGENT_ORDER_ASSIGNED"],
  "meta": { "author": "ops", "version": "1.0" }
}
```

### Поля правила

| Поле             | Тип          | Обязательное | Описание                                    |
|------------------|--------------|:------------:|---------------------------------------------|
| `id`             | String       | ✅           | Уникальный идентификатор правила            |
| `description`    | String       |              | Человеко-читаемое описание                  |
| `priority`       | int          |              | Приоритет (чем выше, тем важнее)            |
| `triggers`       | String[]     | ✅           | События-триггеры                            |
| `active`         | boolean      |              | Включено/выключено                          |
| `conditions`     | String[]     |              | JEXL-выражения (ЕСЛИ)                       |
| `actions`        | String[]     | ✅           | JEXL-скрипты (ТО)                           |
| `producedEvents` | String[]     |              | События, генерируемые после выполнения       |
| `meta`           | Map          |              | Метаданные (автор, версия и т.д.)           |

### Язык условий и действий (JEXL)

Условия и действия пишутся на **Apache Commons JEXL** в строгом режиме с песочницей. Доступны:

- `state` — объект `WarehouseState`
- `f` — экземпляр `RuleFunctions` (хелперы)

**Условия** должны возвращать `boolean`:
```
f.hasPendingOrders(state)
f.hasFreeRobots(state)
```

**Действия** выполняются последовательно в одном контексте:
```
robotId = f.firstFreeRobotId(state)
f.assignRobotToOrder(state, robotId, orderId)
```

---

## Встроенные функции (RuleFunctions)

Все функции доступны в правилах через префикс `f.`.

### Проверки (возвращают boolean)

| Функция                              | Описание                                          |
|--------------------------------------|---------------------------------------------------|
| `hasPendingOrders(state)`            | Есть ли ожидающие заказы                          |
| `hasFreeRobots(state)`               | Есть ли свободные роботы                          |
| `hasUrgentPendingOrders(state)`      | Есть ли срочные ожидающие заказы                  |
| `hasBrokenRobots(state)`             | Есть ли сломанные роботы                          |
| `hasCapableFreeRobotForOrder(state, orderId)` | Есть ли робот с достаточной грузоподъёмностью для заказа |
| `isRobotInOrderDestinationZone(state, robotId, orderId)` | Робот в зоне назначения заказа |

### Поиск (возвращают String / Optional<Robot>)

| Функция                              | Описание                                          |
|--------------------------------------|---------------------------------------------------|
| `firstFreeRobotId(state)`            | ID первого свободного робота                      |
| `firstPendingOrderId(state)`         | ID первого ожидающего заказа                     |
| `firstUrgentPendingOrderId(state)`   | ID первого срочного ожидающего заказа             |
| `bestFreeRobotIdForOrder(state, orderId)` | ID лучшего робота (по грузоподъёмности + расстоянию) |
| `findFirstFreeRobot(state)`          | Optional<Robot> — первый свободный робот          |

### Назначения (возвращают boolean)

| Функция                                              | Описание                                              |
|------------------------------------------------------|-------------------------------------------------------|
| `assignBestRobotToUrgentOrder(state)`                | Назначить лучшего робота на срочный заказ             |
| `assignBestRobotToFirstPendingOrder(state)`          | Назначить лучшего робота на первый ожидающий заказ    |
| `assignHeavyOrderToCapableRobot(state, minWeight)`   | Назначить робота на тяжёлый заказ (вес >= minWeight)  |
| `recoverFromBrokenRobot(state)`                      | Переназначить заказы после сбоя робота                |
| `assignRobotToOrder(state, robotId, orderId)`        | Назначить конкретного робота на заказ                 |

### Утилиты

| Функция                              | Описание                                          |
|--------------------------------------|---------------------------------------------------|
| `explain(message)`                   | Добавить пояснение в контекст выполнения           |
| `emit(event)`                        | Сгенерировать событие в контексте                  |
| `distance(x1, y1, x2, y2)`           | Вычислить расстояние между двумя точками           |

### Алгоритм выбора лучшего робота

Функция `bestFreeRobotIdForOrder` выбирает робота по формуле:

```
score = distance(robot, destinationZone) + (capacity - orderWeight) + zoneBonus
```

Где `zoneBonus = -100`, если робот уже находится в зоне назначения заказа. Чем меньше score — тем лучше робот.

---

## Аудит

### AuditEntry

Каждое выполнение правила записывается в аудит:

| Поле                   | Тип                    | Описание                              |
|------------------------|------------------------|---------------------------------------|
| `traceId`              | String                 | Идентификатор трассировки             |
| `ruleId`               | String                 | ID правила                            |
| `triggeringEvent`      | String                 | Событие, вызвавшее правило             |
| `timestamp`            | Instant                | Временная метка                       |
| `decision`             | String                 | APPLIED / SKIPPED / FAILED            |
| `priority`             | int                    | Приоритет правила                     |
| `evaluatedConditions`  | List<Map>              | Результаты проверки каждого условия   |
| `facts`                | Map                    | Факты, собранные в процессе           |
| `beforeSnapshot`       | Map                    | Снимок состояния до выполнения         |
| `afterSnapshot`        | Map                    | Снимок состояния после выполнения      |
| `producedEvents`       | List<String>           | Сгенерированные события                |
| `explanation`          | String                 | Пояснение (человеко-читаемое)         |

### AuditService

```java
public interface AuditService {
    void record(AuditEntry entry);
    List<AuditEntry> queryByTrace(String traceId);
}
```

Реализация `InMemoryAuditService` использует `ConcurrentLinkedQueue` для потокобезопасного хранения.

---

## Загрузка правил и Hot-Reload

### RuleLoader

Загружает правила из директории `.json` файлов:

```java
RuleLoader ruleLoader = new RuleLoader(rulesDir, registrar);
ruleLoader.loadAll();       // Загрузка всех правил
ruleLoader.startWatcher();  // Запуск WatchService для hot-reload
```

### Поддерживаемые события файловой системы

| Событие              | Действие                              |
|----------------------|---------------------------------------|
| `ENTRY_CREATE`       | Загрузить и зарегистрировать правило   |
| `ENTRY_MODIFY`       | Обновить определение правила          |
| `ENTRY_DELETE`       | Деактивировать правило                |

### RuleRegistrar

Хранит реестр правил в `ConcurrentHashMap<String, AuditedRuleWrapper>`:

- `registerFromDefinition(def)` — регистрация нового правила
- `updateDefinition(def)` — обновление существующего правила
- `deactivate(id)` — деактивация правила

---

## Кейсы использования

### Кейс 1: Срочный заказ (urgent-preempt-1)

**Приоритет:** 100 (наивысший)

**Сценарий:** Поступает срочный заказ. Система должна немедленно назначить на него лучшего доступного робота.

**Условия:**
- Есть срочные ожидающие заказы (`f.hasUrgentPendingOrders(state)`)
- Есть свободные роботы (`f.hasFreeRobots(state)`)

**Действия:**
- Назначить лучшего робота на срочный заказ (`f.assignBestRobotToUrgentOrder(state)`)
- Записать пояснение

**Сгенерированные события:** `ORDER_ASSIGNED`, `URGENT_ORDER_ASSIGNED`

**Результат:** Робот переводится в `BUSY`, заказ — в `PROCESSING`.

---

### Кейс 2: Тяжёлый заказ (heavy-item-assignment-1)

**Приоритет:** 80

**Сценарий:** Заказ содержит тяжёлый товар (вес >= 20 кг). Требуется назначить робота с достаточной грузоподъёмностью.

**Условия:**
- Есть ожидающие заказы (`f.hasPendingOrders(state)`)
- Есть свободные роботы (`f.hasFreeRobots(state)`)

**Действия:**
- Назначить робота на тяжёлый заказ с проверкой грузоподъёмности (`f.assignHeavyOrderToCapableRobot(state, 20.0)`)
- Записать пояснение

**Сгенерированные события:** `HEAVY_ORDER_ASSIGNED`

**Результат:** Если ни один робот не имеет достаточной грузоподъёмности — заказ остаётся в `PENDING`.

---

### Кейс 3: Аварийное восстановление (broken-robot-recovery-1)

**Приоритет:** 90

**Сценарий:** Робот сломался (статус `BROKEN`). Заказы, которые он должен был выполнить, нужно переназначить на других роботов.

**Условия:**
- Есть сломанные роботы (`f.hasBrokenRobots(state)`)
- Есть ожидающие заказы (`f.hasPendingOrders(state)`)
- Есть свободные роботы (`f.hasFreeRobots(state)`)

**Действия:**
- Переназначить заказы (`f.recoverFromBrokenRobot(state)`)
- Записать пояснение

**Сгенерированные события:** `ORDER_REASSIGNED_AFTER_FAILURE`

**Результат:** Первый ожидающий заказ переназначается на ближайшего свободного робота.

---

### Кейс 4: Обычное назначение (capacity-optimized-assignment-1)

**Приоритет:** 60

**Сценарий:** Обычный заказ (не срочный, не тяжёлый). Назначить ближайшего подходящего робота.

**Условия:**
- Есть ожидающие заказы (`f.hasPendingOrders(state)`)
- Есть свободные роботы (`f.hasFreeRobots(state)`)

**Действия:**
- Назначить лучшего робота на первый ожидающий заказ (`f.assignBestRobotToFirstPendingOrder(state)`)
- Записать пояснение

**Сгенерированные события:** `ORDER_ASSIGNED`

**Результат:** Робот выбирается по формуле расстояния + грузоподъёмности.

---

### Пример цепочки событий

```
SYSTEM_STARTUP
  ├── urgent-preempt-1 (priority 100)
  │     └── ORDER_ASSIGNED, URGENT_ORDER_ASSIGNED
  ├── broken-robot-recovery-1 (priority 90)
  │     └── ORDER_REASSIGNED_AFTER_FAILURE
  ├── heavy-item-assignment-1 (priority 80)
  │     └── HEAVY_ORDER_ASSIGNED
  └── capacity-optimized-assignment-1 (priority 60)
        └── ORDER_ASSIGNED
```

---

## Запуск

### Сборка

```bash
./gradlew build
```

### Запуск демо

```bash
./gradlew run
```

Запускает `Main.java` с моковыми данными склада:
- 2 зоны (zone-A, zone-B)
- 3 робота (1 сломанный, 2 свободных)
- 2 товара (лёгкий 5 кг, тяжёлый 35 кг)
- 2 полки
- 2 заказа (срочный лёгкий, обычный тяжёлый)

### Запуск тестов

```bash
./gradlew test
```

### Структура проекта

```
src/
├── main/
│   ├── java/dev/rahmatullin/
│   │   ├── Main.java                    # Точка входа
│   │   ├── audit/                       # Аудит решений
│   │   │   ├── AuditEntry.java
│   │   │   ├── AuditService.java
│   │   │   └── InMemoryAuditService.java
│   │   ├── domain/                      # Доменная модель
│   │   │   ├── WarehouseState.java
│   │   │   ├── Robot.java
│   │   │   ├── Order.java
│   │   │   ├── OrderStatus.java
│   │   │   ├── Item.java
│   │   │   ├── Shelf.java
│   │   │   ├── Zone.java
│   │   │   └── Point.java
│   │   ├── engine/                      # Движок правил
│   │   │   ├── MivarGraphEngine.java
│   │   │   ├── MivarRule.java
│   │   │   └── MockDataLoader.java
│   │   └── rules/                       # Правила и загрузка
│   │       ├── RuleDefinition.java
│   │       ├── RuleExecutionContext.java
│   │       ├── RuleFunctions.java
│   │       ├── RuleLoader.java
│   │       ├── RuleRegistrar.java
│   │       ├── DataDrivenRule.java
│   │       └── AuditedRuleWrapper.java
│   └── resources/config/rules/          # JSON-правила
│       ├── broken-robot-recovery.json
│       ├── capacity-optimized-assignment.json
│       ├── heavy-item-assignment.json
│       └── urgent-preempt.json
└── test/
    ├── java/dev/rahmatullin/rules/
    │   └── DataDrivenRuleTest.java
    └── resources/
        └── mock_state.json
```