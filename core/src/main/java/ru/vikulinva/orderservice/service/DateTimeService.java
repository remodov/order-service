package ru.vikulinva.orderservice.service;

import java.time.Instant;

/**
 * Абстракция «текущего времени» — единственный источник {@code now()} для всего домена и use case'ов.
 * Production-реализация (на {@code Clock.systemUTC()}) — бин в bootstrap; в тестах подменяется {@code @MockitoBean}.
 */
@FunctionalInterface
public interface DateTimeService {

    Instant now();
}
