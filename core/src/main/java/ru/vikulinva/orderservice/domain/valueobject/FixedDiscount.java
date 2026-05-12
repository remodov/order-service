package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;

/** Фиксированная скидка на сумму {@code amount}; при применении обрезается до базы заказа. */
public record FixedDiscount(Money amount) implements Discount {

    public FixedDiscount {
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Fixed discount amount must be positive");
        }
    }

    public static FixedDiscount of(Money amount) {
        return new FixedDiscount(amount);
    }

    @Override
    public Money amountFor(Money orderBase) {
        return amount.min(orderBase);
    }
}
