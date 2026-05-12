package ru.vikulinva.orderservice.config;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vikulinva.orderservice.service.DateTimeService;
import ru.vikulinva.orderservice.service.UuidGenerator;

/**
 * Production-реализации системных «источников недетерминизма» ({@code Clock}, {@link DateTimeService},
 * {@link UuidGenerator}). Каждый бин под {@link ConditionalOnMissingBean} — тесты подменяют их через {@code @MockitoBean}.
 */
@Configuration
public class ServiceBeansConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public DateTimeService dateTimeService(Clock clock) {
        return () -> Instant.now(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public UuidGenerator uuidGenerator() {
        return UUID::randomUUID;
    }
}
