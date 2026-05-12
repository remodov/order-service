package ru.vikulinva.orderservice.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Публикация доменного события из Outbox во внешний транспорт. Реализации: лог-стаб (сейчас),
 * Kafka-publisher (Ф4 — подменит стаб через {@code @ConditionalOnMissingBean}).
 */
public interface ExternalEventPublisher {

    void publish(OutboxMessage message);

    /** Снимок строки {@code outbox} для публикации. */
    record OutboxMessage(UUID id, UUID aggregateId, String aggregateType, String eventType,
                         String payloadJson, Instant occurredAt) {
    }
}
