package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор позиции заказа (уникален в рамках агрегата {@code Order}). */
public record OrderItemId(UUID value) implements ValueObject {

    public OrderItemId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderItemId of(UUID value) {
        return new OrderItemId(value);
    }

    public static OrderItemId of(String value) {
        return new OrderItemId(UUID.fromString(value));
    }
}
