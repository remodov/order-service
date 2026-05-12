package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор резерва остатка в Inventory-сервисе. */
public record ReservationId(UUID value) implements ValueObject {

    public ReservationId {
        Objects.requireNonNull(value, "value");
    }

    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId of(String value) {
        return new ReservationId(UUID.fromString(value));
    }
}
