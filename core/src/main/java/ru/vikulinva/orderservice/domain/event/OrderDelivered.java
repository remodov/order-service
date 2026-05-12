package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Заказ вручён покупателю ({@code SHIPPED → DELIVERED}). Внешнее, {@code marketplace.orders.v1}. Стартует окно спора 14 дней. */
@Getter
public final class OrderDelivered extends DomainEvent {

    private final Instant deliveredAt;

    public OrderDelivered(OrderId orderId, Instant deliveredAt) {
        super("Order", orderId.value().toString());
        this.deliveredAt = deliveredAt;
    }
}
