package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** По заказу уже открыт спор. HTTP 409, {@code DISPUTE_ALREADY_OPEN}. */
public class DisputeAlreadyOpenException extends OrderDomainException {

    public DisputeAlreadyOpenException(OrderId orderId) {
        super("DISPUTE_ALREADY_OPEN", "Dispute already open for order " + orderId.value());
    }
}
