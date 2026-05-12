package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

/** Количество единиц товара в позиции заказа: целое от 1 до 999. */
public record Quantity(int value) implements ValueObject {

    public static final int MIN = 1;
    public static final int MAX = 999;

    public Quantity {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("Quantity must be in [" + MIN + ".." + MAX + "], got " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value);
    }
}
