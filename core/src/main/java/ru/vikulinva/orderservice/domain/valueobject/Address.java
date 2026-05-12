package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

/**
 * Адрес доставки. {@code pickupPointCode} — код ПВЗ, может быть {@code null} (доставка до двери).
 * Содержит PII — шифрование at-rest настраивается на уровне persistence (Ф2), здесь — просто VO.
 */
public record Address(String country, String city, String street, String postalCode, String pickupPointCode)
    implements ValueObject {

    public Address {
        requireNotBlank(country, "country");
        requireNotBlank(city, "city");
        requireNotBlank(street, "street");
        requireNotBlank(postalCode, "postalCode");
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
