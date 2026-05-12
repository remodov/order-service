package ru.vikulinva.orderservice.domain.event;

import java.math.BigDecimal;
import java.util.UUID;
import ru.vikulinva.ddd.ValueObject;

/** Снимок позиции заказа для payload событий (без ссылки на сущность {@code OrderItem}). */
public record OrderItemSnapshot(UUID productId, UUID sellerId, int quantity, BigDecimal unitPrice)
    implements ValueObject {
}
