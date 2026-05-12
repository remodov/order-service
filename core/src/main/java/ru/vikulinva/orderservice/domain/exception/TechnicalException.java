package ru.vikulinva.orderservice.domain.exception;

/** Непредвиденная техническая ошибка / нарушение внутреннего инварианта (баг). Маппится в 500 {@code INTERNAL_ERROR}. */
public class TechnicalException extends AbstractCodedException {

    public TechnicalException(String message) {
        super("INTERNAL_ERROR", message);
    }

    public TechnicalException(String message, Throwable cause) {
        super("INTERNAL_ERROR", message, cause);
    }
}
