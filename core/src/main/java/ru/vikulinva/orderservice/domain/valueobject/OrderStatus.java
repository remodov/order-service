package ru.vikulinva.orderservice.domain.valueobject;

/**
 * Фаза жизненного цикла заказа. Терминальные ({@link #COMPLETED}, {@link #EXPIRED},
 * {@link #REFUNDED}) — переходов из них нет. См. {@code docs/spec/aggregates/order.md}.
 */
public enum OrderStatus {

    DRAFT,
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    REFUNDED,
    DISPUTE;

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == REFUNDED;
    }
}
