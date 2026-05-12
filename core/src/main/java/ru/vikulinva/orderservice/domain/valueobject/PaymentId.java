package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Идентификатор платежа в Payment-сервисе (последняя успешная попытка). */
public record PaymentId(UUID value) implements ValueObject {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }

    public static PaymentId of(String value) {
        return new PaymentId(UUID.fromString(value));
    }
}
