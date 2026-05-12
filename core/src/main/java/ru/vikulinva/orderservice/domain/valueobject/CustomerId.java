package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор покупателя (ссылка на User-сервис по ID). */
public record CustomerId(UUID value) implements ValueObject {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }
}
