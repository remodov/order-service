package ru.vikulinva.orderservice.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/** Окно спора закрыто, заказ завершён ({@code DELIVERED → COMPLETED}). Внешнее, {@code marketplace.orders.v1}. Settlement начисляет выручку продавцу. */
@Getter
public final class OrderCompleted extends DomainEvent {

    private final UUID sellerId;
    private final BigDecimal totalAmount;
    private final String currency;
    private final Instant closedAt;

    public OrderCompleted(OrderId orderId, SellerId sellerId, Money total, Instant closedAt) {
        super("Order", orderId.value().toString());
        this.sellerId = sellerId.value();
        this.totalAmount = total.amount();
        this.currency = total.currency().getCurrencyCode();
        this.closedAt = closedAt;
    }
}
