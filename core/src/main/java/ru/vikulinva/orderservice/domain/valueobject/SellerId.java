package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор продавца (ссылка на Seller-сервис по ID). */
public record SellerId(UUID value) implements ValueObject {

    public SellerId {
        Objects.requireNonNull(value, "value");
    }

    public static SellerId of(UUID value) {
        return new SellerId(value);
    }

    public static SellerId of(String value) {
        return new SellerId(UUID.fromString(value));
    }
}
