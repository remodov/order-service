package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** Заказ не существует (или нет доступа — ABAC решает на уровне handler'а). HTTP 404, {@code ORDER_NOT_FOUND}. */
public class OrderNotFoundException extends OrderDomainException {

    public OrderNotFoundException(OrderId orderId) {
        super("ORDER_NOT_FOUND", "Order not found: " + orderId.value());
    }
}
