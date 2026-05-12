package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/** К заказу уже применён промокод; замена — через {@code removePromo} + {@code applyPromo}. HTTP 409, {@code PROMO_ALREADY_APPLIED} ({@code BR-003}). */
public class PromoAlreadyAppliedException extends OrderDomainException {

    public PromoAlreadyAppliedException(OrderId orderId) {
        super("PROMO_ALREADY_APPLIED", "Order " + orderId.value() + " already has a promo applied");
    }
}
