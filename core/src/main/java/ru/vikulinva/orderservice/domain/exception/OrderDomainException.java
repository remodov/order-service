package ru.vikulinva.orderservice.domain.exception;

import lombok.Getter;

/**
 * Базовое доменное исключение Order. {@code code} — стабильный машинный идентификатор,
 * совпадающий с каталогом ошибок ({@code docs/spec/13-order-service-errors.md}); HTTP-маппинг
 * в RFC 9457 ProblemDetails делается в adapter-in-rest (Ф3, {@code /ucp-error-handling-design}).
 */
@Getter
public class OrderDomainException extends RuntimeException {

    private final String code;

    public OrderDomainException(String code, String message) {
        super(message);
        this.code = code;
    }
}
