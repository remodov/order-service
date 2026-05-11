package ru.vikulinva.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security для профиля {@code local}: всё открыто ({@code permitAll}), чтобы дёргать API
 * из curl/Postman без поднятия Keycloak. {@code @EnableMethodSecurity} оставляем —
 * {@code @PreAuthorize}-аннотации (появятся в Ф7) остаются активными; для проверки
 * роле-зависимых сценариев локально используйте {@code with(jwt())} в тестах или живой IdP.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
