package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

/** Причина открытия спора покупателем (свободный текст). */
public record DisputeReason(String text) implements ValueObject {

    public static final int MAX_LENGTH = 2000;

    public DisputeReason {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Dispute reason must not be blank");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Dispute reason too long: " + text.length() + " > " + MAX_LENGTH);
        }
    }

    public static DisputeReason of(String text) {
        return new DisputeReason(text);
    }
}
