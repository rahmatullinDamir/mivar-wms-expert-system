package dev.rahmatullin.engine;

import dev.rahmatullin.domain.WarehouseState;
import java.util.Set;

/**
 * Интерфейс-контракт для реализации продукционных правил в миварном графе.
 */
public interface MivarRule {

    /**
     * Возвращает список строковых триггеров (изменившихся свойств),
     * при фиксации которых движок должен вызвать это правило.
     */
    Set<String> getTriggers();

    /**
     * ЕСЛИ: Проверка применимости правила к текущему состоянию склада.
     */
    boolean isApplicable(WarehouseState state);

    /**
     * ТО: Выполнение бизнес-логики правила (изменение состояния объектов).
     * @return Set строк-событий, которые это правило сгенерировало в результате своей работы.
     */
    Set<String> execute(WarehouseState state);
}