package ru.vikulinva.orderservice.domain.exception;

import lombok.Getter;

/**
 * Базовый класс для всех «кодированных» исключений сервиса: несёт стабильный машинный {@code code}
 * (из каталога ошибок {@code docs/spec/13-order-service-errors.md}). HTTP-маппинг — в {@code GlobalExceptionHandler}.
 */
@Getter
public abstract class AbstractCodedException extends RuntimeException {

    private final String code;

    protected AbstractCodedException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected AbstractCodedException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
