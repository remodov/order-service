package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор заказа. */
public record OrderId(UUID value) implements ValueObject {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId of(String value) {
        return new OrderId(UUID.fromString(value));
    }
}
