package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор товара (ссылка на Catalog-сервис по ID). */
public record ProductId(UUID value) implements ValueObject {

    public ProductId {
        Objects.requireNonNull(value, "value");
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    public static ProductId of(String value) {
        return new ProductId(UUID.fromString(value));
    }
}
