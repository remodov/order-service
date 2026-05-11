package ru.vikulinva.orderservice.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Jackson для сериализации доменных событий в JSONB-payload Outbox.
 *
 * <p>Доменные события — {@code final}-классы с {@code private final}-полями и геттерами;
 * при дефолтной видимости Jackson может не увидеть часть полей и записать пустой/обрезанный
 * payload. Делаем поля видимыми и не валимся на «пустых» бинах. {@code JavaTimeModule}
 * подключается Spring Boot автоматически (jackson-datatype-jsr310 на classpath).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer eventPayloadObjectMapperCustomizer() {
        return builder -> builder.postConfigurer((ObjectMapper mapper) -> {
            mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });
    }
}
