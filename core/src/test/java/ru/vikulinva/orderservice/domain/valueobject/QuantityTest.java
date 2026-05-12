package ru.vikulinva.orderservice.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void acceptsBounds() {
        assertThat(Quantity.of(1).value()).isEqualTo(1);
        assertThat(Quantity.of(999).value()).isEqualTo(999);
    }

    @Test
    void rejectsZeroAndNegative() {
        assertThatThrownBy(() -> Quantity.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantity.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAboveMax() {
        assertThatThrownBy(() -> Quantity.of(1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusAddsUp() {
        assertThat(Quantity.of(2).plus(Quantity.of(3))).isEqualTo(Quantity.of(5));
    }
}
