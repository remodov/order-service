package ru.vikulinva.orderservice.domain.event;

import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Резерв остатка не удался — заказ возвращён в {@code DRAFT}. Внешнее, {@code marketplace.orders.v1}. Notification сообщает покупателю. */
@Getter
public final class OrderReservationFailed extends DomainEvent {

    private final UUID customerId;
    private final String reason;

    public OrderReservationFailed(OrderId orderId, CustomerId customerId, String reason) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.reason = reason;
    }
}
