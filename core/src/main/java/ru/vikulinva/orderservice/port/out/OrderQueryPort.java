package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.usecase.query.dto.OrderSummary;
import ru.vikulinva.orderservice.usecase.query.dto.PageResult;

/**
 * Read-side порт для проекций заказов. Реализация — jOOQ в
 * {@code adapter-out-postgres} (без загрузки полного агрегата).
 */
@OutboundPort
public interface OrderQueryPort {

    /**
     * Список заказов покупателя с фильтром по статусу. Сортировка —
     * {@code createdAt DESC} (новые сверху). Параметры пагинации:
     * {@code page} ≥ 0, {@code size} ∈ [1; 100].
     *
     * @param status опциональный фильтр; {@code null} — без фильтра
     */
    PageResult<OrderSummary> listByCustomer(CustomerId customerId,
                                              OrderStatus status,
                                              int page,
                                              int size);
}
