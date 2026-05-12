package ru.vikulinva.orderservice.domain.exception;

import lombok.Getter;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;

/** Команда не применима к текущему статусу заказа. HTTP 409, {@code ORDER_INVALID_STATE}. */
@Getter
public class OrderInvalidStateException extends OrderDomainException {

    private final OrderStatus actual;
    private final transient Object expected;

    public OrderInvalidStateException(String operation, OrderStatus actual, Object expected) {
        super("ORDER_INVALID_STATE",
            "Cannot " + operation + ": order is in " + actual + ", expected " + expected);
        this.actual = actual;
        this.expected = expected;
    }
}
