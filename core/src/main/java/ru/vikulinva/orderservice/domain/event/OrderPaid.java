package ru.vikulinva.orderservice.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.PaymentId;

/** Платёж подтверждён ({@code PENDING_PAYMENT → PAID}). Внешнее, {@code marketplace.orders.v1}. Notification — чек, Settlement — учёт, Inventory — commit резерва. */
@Getter
public final class OrderPaid extends DomainEvent {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant paidAt;

    public OrderPaid(OrderId orderId, PaymentId paymentId, Money amount, Instant paidAt) {
        super("Order", orderId.value().toString());
        this.paymentId = paymentId.value();
        this.amount = amount.amount();
        this.currency = amount.currency().getCurrencyCode();
        this.paidAt = paidAt;
    }
}
