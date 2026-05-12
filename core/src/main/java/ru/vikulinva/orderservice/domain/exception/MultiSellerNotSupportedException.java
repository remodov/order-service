package ru.vikulinva.orderservice.domain.exception;

import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/** Попытка добавить в заказ товар другого продавца. HTTP 400, {@code MULTI_SELLER_NOT_SUPPORTED} ({@code BR-014}). */
public class MultiSellerNotSupportedException extends OrderDomainException {

    public MultiSellerNotSupportedException(SellerId existing, SellerId attempted) {
        super("MULTI_SELLER_NOT_SUPPORTED",
            "Order already has items from seller " + existing.value() + "; cannot add seller " + attempted.value());
    }
}
