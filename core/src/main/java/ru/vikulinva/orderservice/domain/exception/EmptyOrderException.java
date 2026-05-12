package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Попытка подтвердить ({@code confirm}) заказ без позиций. HTTP 400, {@code EMPTY_ORDER}. */
public class EmptyOrderException extends OrderDomainException {

    public EmptyOrderException(OrderId orderId) {
        super("EMPTY_ORDER", "Cannot confirm order without items: " + orderId.value());
    }
}
