package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;

/** Заказ отменён покупателем ({@code → CANCELLED}). Внешнее, {@code marketplace.orders.v1}. Inventory снимает резерв (если был), Notification подтверждает. */
@Getter
public final class OrderCancelled extends DomainEvent {

    private final UUID customerId;
    private final UUID reservationId;
    private final Instant cancelledAt;

    public OrderCancelled(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant cancelledAt) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.reservationId = reservationId == null ? null : reservationId.value();
        this.cancelledAt = cancelledAt;
    }
}
