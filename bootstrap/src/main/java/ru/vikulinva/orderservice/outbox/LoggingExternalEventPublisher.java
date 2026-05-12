package ru.vikulinva.orderservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;

/**
 * Лог-стаб {@link ExternalEventPublisher} до подключения Kafka (Ф4). Перебивается Kafka-реализацией
 * через {@code @ConditionalOnMissingBean(name = "kafkaExternalEventPublisher")}.
 */
@Component
@Slf4j
@ConditionalOnMissingBean(name = "kafkaExternalEventPublisher")
public class LoggingExternalEventPublisher implements ExternalEventPublisher {

    @Override
    public void publish(OutboxMessage message) {
        log.info("[outbox-stub] publish event {} aggregateId={} occurredAt={} payload={}",
            message.eventType(), message.aggregateId(), message.occurredAt(), message.payloadJson());
    }
}
