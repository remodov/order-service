package ru.vikulinva.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security для профиля {@code integration-test}: {@code permitAll}, без JWK от Keycloak.
 * {@code @EnableMethodSecurity} оставлен — в {@code @SpringBootTest} роли подаются через
 * {@code with(jwt().authorities(...))} из {@code spring-security-test}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("integration-test")
public class TestJwtConfiguration {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
