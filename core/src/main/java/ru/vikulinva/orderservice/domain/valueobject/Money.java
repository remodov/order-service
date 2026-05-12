package ru.vikulinva.orderservice.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import ru.vikulinva.ddd.ValueObject;

/**
 * Денежная сумма: неотрицательная {@code amount} с фиксированной шкалой 2 знака + {@code currency}
 * (в этой версии всегда RUB). Immutable; арифметика возвращает новый {@code Money}.
 */
public record Money(BigDecimal amount, Currency currency) implements ValueObject {

    public static final Currency RUB = Currency.getInstance("RUB");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must not be negative: " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money rub(BigDecimal amount) {
        return new Money(amount, RUB);
    }

    public static Money rub(String amount) {
        return new Money(new BigDecimal(amount), RUB);
    }

    public static Money rub(long amount) {
        return new Money(BigDecimal.valueOf(amount), RUB);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /** Вычитание; результат не может быть отрицательным (нарушение → исключение). */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("Money subtraction underflow: " + amount + " - " + other.amount);
        }
        return new Money(result, currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative: " + factor);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.signum() < 0) {
            throw new IllegalArgumentException("factor must not be negative: " + factor);
        }
        return new Money(amount.multiply(factor), currency);
    }

    /** Меньший из двух (для обрезки скидки до суммы заказа, {@code BR-012}). */
    public Money min(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) <= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
