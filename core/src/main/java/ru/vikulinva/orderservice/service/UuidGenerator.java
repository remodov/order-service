package ru.vikulinva.orderservice.service;

import java.util.UUID;

/**
 * Абстракция генерации UUID — источник новых идентификаторов ({@code OrderId}, {@code OrderItemId}, …).
 * Production-реализация ({@code UUID::randomUUID}) — бин в bootstrap; в тестах подменяется {@code @MockitoBean}.
 */
@FunctionalInterface
public interface UuidGenerator {

    UUID generate();
}
