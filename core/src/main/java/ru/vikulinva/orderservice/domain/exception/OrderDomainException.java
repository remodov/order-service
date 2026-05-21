package ru.vikulinva.orderservice.domain.exception;

/**
 * Базовое доменное исключение Order — нарушение бизнес-инварианта / недопустимый переход.
 * {@code code} совпадает с каталогом ошибок ({@code docs/spec/aggregates/order.md});
 * HTTP-маппинг в RFC 9457 ProblemDetails — в {@code adapter-in-rest/GlobalExceptionHandler}.
 */
public class OrderDomainException extends AbstractCodedException {

    public OrderDomainException(String code, String message) {
        super(code, message);
    }
}
