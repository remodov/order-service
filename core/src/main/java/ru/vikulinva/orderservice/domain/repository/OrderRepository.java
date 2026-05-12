package ru.vikulinva.orderservice.domain.repository;

import java.util.Optional;
import ru.vikulinva.ddd.AggregateRepository;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/**
 * Репозиторий агрегата {@code Order}. Реализация — в {@code adapter-out-postgres} (jOOQ, Ф2);
 * {@code save} атомарно сохраняет агрегат целиком и публикует накопленные доменные события в Outbox
 * в той же транзакции ({@code BR-015}).
 */
public interface OrderRepository extends AggregateRepository<Order, OrderId> {

    /**
     * Загрузка под пессимистичную блокировку ({@code SELECT … FOR UPDATE}) — для command-handler'ов,
     * которые читают-меняют-сохраняют заказ в одной транзакции (например {@code ConfirmOrder}).
     */
    Optional<Order> findByIdForUpdate(OrderId id);
}
