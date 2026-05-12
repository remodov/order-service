package ru.vikulinva.orderservice.domain.exception;

/** Невалидные входные данные, обнаруженные в handler'е (за рамками Jakarta @Valid). Маппится в 400. */
public class ValidationException extends AbstractCodedException {

    public ValidationException(String code, String message) {
        super(code, message);
    }
}
