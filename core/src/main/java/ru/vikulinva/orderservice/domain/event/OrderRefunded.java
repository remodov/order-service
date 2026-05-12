package ru.vikulinva.orderservice.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/** Деньги возвращены покупателю ({@code → REFUNDED}). Внешнее, {@code marketplace.orders.v1}. Settlement — компенсация (минус-баланс продавца при {@code BR-009}), Notification — подтверждение. */
@Getter
public final class OrderRefunded extends DomainEvent {

    private final UUID customerId;
    private final UUID sellerId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant refundedAt;

    public OrderRefunded(OrderId orderId, CustomerId customerId, SellerId sellerId, Money amount, Instant refundedAt) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.sellerId = sellerId.value();
        this.amount = amount.amount();
        this.currency = amount.currency().getCurrencyCode();
        this.refundedAt = refundedAt;
    }
}
