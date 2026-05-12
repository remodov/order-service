package ru.vikulinva.orderservice.domain.event;

import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Заказ создан (статус {@code DRAFT}). Внутреннее событие — обновляет Read Model {@code order_summaries}. */
@Getter
public final class OrderCreated extends DomainEvent {

    private final UUID customerId;

    public OrderCreated(OrderId orderId, CustomerId customerId) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
    }
}
