package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Продавец передал посылку курьеру/в ПВЗ ({@code PAID → SHIPPED}). Внешнее, {@code marketplace.orders.v1}. Notification — трек-номер покупателю. */
@Getter
public final class OrderShipped extends DomainEvent {

    private final String shipmentRef;
    private final Instant shippedAt;

    public OrderShipped(OrderId orderId, String shipmentRef, Instant shippedAt) {
        super("Order", orderId.value().toString());
        this.shipmentRef = shipmentRef;
        this.shippedAt = shippedAt;
    }
}
