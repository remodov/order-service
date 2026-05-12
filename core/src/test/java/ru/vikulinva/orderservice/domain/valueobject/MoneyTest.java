package ru.vikulinva.orderservice.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesScaleToTwoDigits() {
        assertThat(Money.rub("1.5")).isEqualTo(Money.rub("1.50"));
        assertThat(Money.rub("1.005").amount()).isEqualByComparingTo("1.01");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.rub("-0.01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticReturnsNewInstance() {
        Money ten = Money.rub(10);
        assertThat(ten.add(Money.rub(5))).isEqualTo(Money.rub(15));
        assertThat(ten.subtract(Money.rub(3))).isEqualTo(Money.rub(7));
        assertThat(ten.multiply(3)).isEqualTo(Money.rub(30));
        assertThat(ten).isEqualTo(Money.rub(10));
    }

    @Test
    void subtractUnderflowThrows() {
        assertThatThrownBy(() -> Money.rub(5).subtract(Money.rub(6))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minPicksSmaller() {
        assertThat(Money.rub(5).min(Money.rub(8))).isEqualTo(Money.rub(5));
        assertThat(Money.rub(8).min(Money.rub(5))).isEqualTo(Money.rub(5));
    }

    @Test
    void comparisons() {
        assertThat(Money.rub(100).isGreaterThanOrEqual(Money.rub(100))).isTrue();
        assertThat(Money.rub(99).isLessThan(Money.rub(100))).isTrue();
        assertThat(Money.zero(Money.RUB).isZero()).isTrue();
    }

    @Test
    void currencyMismatchThrows() {
        Money usd = Money.of(BigDecimal.ONE, java.util.Currency.getInstance("USD"));
        assertThatThrownBy(() -> Money.rub(1).add(usd)).isInstanceOf(IllegalArgumentException.class);
    }
}
