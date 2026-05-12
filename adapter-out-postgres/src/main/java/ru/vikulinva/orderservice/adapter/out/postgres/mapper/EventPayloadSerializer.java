package ru.vikulinva.orderservice.adapter.out.postgres.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.vikulinva.ddd.DomainEvent;

/**
 * Сериализация доменного события в JSON для JSONB-колонки {@code outbox.payload}.
 * Использует общий {@link ObjectMapper} (с {@code Visibility.ANY} из {@code JacksonConfig} bootstrap'а),
 * иначе payload получится обрезанным до полей базового {@code DomainEvent}.
 */
@Component
@RequiredArgsConstructor
public class EventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize domain event " + event.getClass().getSimpleName(), e);
        }
    }
}
