package ru.vikulinva.orderservice.domain.exception;

/**
 * Сбой при обращении к внешней системе (Catalog / Payment / Inventory). Не вина клиента и не баг —
 * деградация зависимости. Маппится в 502/504. Конкретные подклассы — в out-adapter'ах.
 */
public class IntegrationException extends AbstractCodedException {

    public IntegrationException(String code, String message) {
        super(code, message);
    }

    public IntegrationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
