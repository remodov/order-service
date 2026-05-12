package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;

/** Платёж отклонён — заказ возвращён в {@code DRAFT}. Внешнее, {@code marketplace.orders.v1}. Inventory снимает резерв, Notification предлагает перезаказ. */
@Getter
public final class OrderPaymentFailed extends DomainEvent {

    private final UUID customerId;
    private final UUID reservationId;
    private final Instant failedAt;

    public OrderPaymentFailed(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant failedAt) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.reservationId = reservationId == null ? null : reservationId.value();
        this.failedAt = failedAt;
    }
}
