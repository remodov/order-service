package ru.vikulinva.orderservice.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Production-реализации системных «источников недетерминизма».
 *
 * <p>Сейчас здесь только {@link Clock} (UTC). Доменные интерфейсы {@code DateTimeService}
 * и {@code UuidGenerator} в {@code core/service/} появятся в Ф1 ({@code /ucp-ddd-tactical-design}) —
 * тогда сюда добавятся их production-бины поверх {@code Clock} / {@code UUID::randomUUID},
 * каждый под {@link ConditionalOnMissingBean}, чтобы тесты могли переопределять через {@code @MockitoBean}.
 */
@Configuration
public class ServiceBeansConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
