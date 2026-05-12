package ru.vikulinva.orderservice.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscountTest {

    @Test
    void percentageOfBase() {
        Discount d = PercentageDiscount.of(BigDecimal.valueOf(10));
        assertThat(d.amountFor(Money.rub(1000))).isEqualTo(Money.rub(100));
    }

    @Test
    void percentageClampedToBase() {
        Discount d = PercentageDiscount.of(BigDecimal.valueOf(100));
        assertThat(d.amountFor(Money.rub(200))).isEqualTo(Money.rub(200));
    }

    @Test
    void percentageRejectsOutOfRange() {
        assertThatThrownBy(() -> PercentageDiscount.of(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PercentageDiscount.of(BigDecimal.valueOf(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedAppliedDirectly() {
        Discount d = FixedDiscount.of(Money.rub(150));
        assertThat(d.amountFor(Money.rub(1000))).isEqualTo(Money.rub(150));
    }

    @Test
    void fixedClampedToBase() {
        Discount d = FixedDiscount.of(Money.rub(500));
        assertThat(d.amountFor(Money.rub(300))).isEqualTo(Money.rub(300));
    }

    @Test
    void fixedRejectsZero() {
        assertThatThrownBy(() -> FixedDiscount.of(Money.zero(Money.RUB))).isInstanceOf(IllegalArgumentException.class);
    }
}
