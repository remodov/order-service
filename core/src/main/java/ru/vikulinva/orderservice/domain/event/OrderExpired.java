package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;

/** Оплата не пришла за 15 минут ({@code PENDING_PAYMENT → EXPIRED}). Внешнее, {@code marketplace.orders.v1}. Inventory снимает резерв. */
@Getter
public final class OrderExpired extends DomainEvent {

    private final UUID reservationId;
    private final Instant expiredAt;

    public OrderExpired(OrderId orderId, ReservationId reservationId, Instant expiredAt) {
        super("Order", orderId.value().toString());
        this.reservationId = reservationId == null ? null : reservationId.value();
        this.expiredAt = expiredAt;
    }
}
