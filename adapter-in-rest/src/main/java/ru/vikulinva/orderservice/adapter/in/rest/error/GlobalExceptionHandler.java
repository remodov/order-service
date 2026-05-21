package ru.vikulinva.orderservice.adapter.in.rest.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vikulinva.orderservice.domain.exception.AbstractCodedException;
import ru.vikulinva.orderservice.domain.exception.IntegrationException;
import ru.vikulinva.orderservice.domain.exception.ValidationException;

/**
 * Единая трансляция исключений в RFC 9457 {@code application/problem+json}. Каждое тело несёт стабильный
 * {@code code} из каталога ошибок ({@code docs/spec/aggregates/order.md}), {@code type} (URI ошибки),
 * {@code title}, {@code detail}, {@code instance}, и {@code violations} для валидации.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TYPE_PREFIX = "https://vikulin-va.ru/errors/";

    /** Соответствие {@code code} → HTTP-статус по docs/spec/aggregates/order.md. */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
        Map.entry("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND),
        Map.entry("PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND),
        Map.entry("ORDER_INVALID_STATE", HttpStatus.CONFLICT),
        Map.entry("PROMO_ALREADY_APPLIED", HttpStatus.CONFLICT),
        Map.entry("DISPUTE_ALREADY_OPEN", HttpStatus.CONFLICT),
        Map.entry("OUT_OF_STOCK", HttpStatus.CONFLICT),
        Map.entry("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT),
        Map.entry("EMPTY_ORDER", HttpStatus.BAD_REQUEST),
        Map.entry("ORDER_BELOW_MINIMUM", HttpStatus.BAD_REQUEST),
        Map.entry("MULTI_SELLER_NOT_SUPPORTED", HttpStatus.BAD_REQUEST),
        Map.entry("PROMO_INVALID", HttpStatus.BAD_REQUEST),
        Map.entry("PROMO_NOT_APPLICABLE", HttpStatus.BAD_REQUEST),
        Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
        Map.entry("PAYMENT_FAILED", HttpStatus.UNPROCESSABLE_ENTITY),
        Map.entry("REFUND_TOO_LATE", HttpStatus.UNPROCESSABLE_ENTITY),
        Map.entry("PAYMENT_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT),
        Map.entry("FORBIDDEN", HttpStatus.FORBIDDEN),
        Map.entry("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
        Map.entry("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR));

    @ExceptionHandler(AbstractCodedException.class)
    public ProblemDetail handleCoded(AbstractCodedException ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex);
        if (status.is5xxServerError()) {
            log.error("Coded exception {} on {} {}", ex.getCode(), request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.warn("Coded exception {} on {} {}: {}", ex.getCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        return problem(status, ex.getCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request.getRequestURI());
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
            .map(cv -> Map.of("field", String.valueOf(cv.getPropertyPath()), "message", cv.getMessage()))
            .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request.getRequestURI());
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", request.getRequestURI());
    }

    private static HttpStatus resolveStatus(AbstractCodedException ex) {
        HttpStatus mapped = STATUS_BY_CODE.get(ex.getCode());
        if (mapped != null) {
            return mapped;
        }
        if (ex.getCode().endsWith("_TIMEOUT")) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (ex instanceof IntegrationException) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (ex instanceof ValidationException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("violations", List.of());
        problem.setInstance(URI.create(instance));
        return problem;
    }
}
