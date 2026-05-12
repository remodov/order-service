package ru.vikulinva.orderservice.domain.exception;

import java.time.Instant;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Окно открытия спора (14 дней с доставки) истекло. HTTP 422, {@code REFUND_TOO_LATE} ({@code BR-007}). */
public class DisputeWindowClosedException extends OrderDomainException {

    public DisputeWindowClosedException(OrderId orderId, Instant deliveredAt, Instant now) {
        super("REFUND_TOO_LATE",
            "Dispute window for order " + orderId.value() + " closed: delivered at " + deliveredAt + ", now " + now);
    }
}
