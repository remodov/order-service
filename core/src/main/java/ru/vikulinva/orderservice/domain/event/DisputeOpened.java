package ru.vikulinva.orderservice.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.DisputeReason;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/** Покупатель открыл спор ({@code DELIVERED → DISPUTE}). Внешнее, {@code marketplace.orders.v1}. Notification — продавцу (3 дня на ответ), Admin BFF — в очередь споров. */
@Getter
public final class DisputeOpened extends DomainEvent {

    private final UUID customerId;
    private final UUID sellerId;
    private final String reason;
    private final Instant openedAt;

    public DisputeOpened(OrderId orderId, CustomerId customerId, SellerId sellerId, DisputeReason reason, Instant openedAt) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.sellerId = sellerId.value();
        this.reason = reason.text();
        this.openedAt = openedAt;
    }
}
