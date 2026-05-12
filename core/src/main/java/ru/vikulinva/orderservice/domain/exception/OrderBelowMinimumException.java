package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.Money;

/** {@code total} меньше минимальной суммы заказа при {@code confirm}. HTTP 400, {@code ORDER_BELOW_MINIMUM} ({@code BR-013}). */
public class OrderBelowMinimumException extends OrderDomainException {

    public OrderBelowMinimumException(Money total, Money minimum) {
        super("ORDER_BELOW_MINIMUM",
            "Order total " + total.amount() + " " + total.currency()
                + " is below minimum " + minimum.amount() + " " + minimum.currency());
    }
}
