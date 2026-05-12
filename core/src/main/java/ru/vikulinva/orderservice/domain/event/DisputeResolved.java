package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.DisputeDecision;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Оператор закрыл спор ({@code DISPUTE → REFUNDED} | {@code DISPUTE → COMPLETED}). Внешнее, {@code marketplace.orders.v1}. Notification — финальное уведомление. */
@Getter
public final class DisputeResolved extends DomainEvent {

    private final DisputeDecision decision;
    private final Instant resolvedAt;

    public DisputeResolved(OrderId orderId, DisputeDecision decision, Instant resolvedAt) {
        super("Order", orderId.value().toString());
        this.decision = decision;
        this.resolvedAt = resolvedAt;
    }
}
