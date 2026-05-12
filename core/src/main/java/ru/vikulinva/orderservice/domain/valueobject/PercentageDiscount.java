package ru.vikulinva.orderservice.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Процентная скидка: {@code percentage} в диапазоне (0..100]. */
public record PercentageDiscount(BigDecimal percentage) implements Discount {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PercentageDiscount {
        Objects.requireNonNull(percentage, "percentage");
        if (percentage.signum() <= 0 || percentage.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Percentage must be in (0..100], got " + percentage);
        }
    }

    public static PercentageDiscount of(BigDecimal percentage) {
        return new PercentageDiscount(percentage);
    }

    @Override
    public Money amountFor(Money orderBase) {
        BigDecimal raw = orderBase.amount().multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return Money.of(raw, orderBase.currency()).min(orderBase);
    }
}
