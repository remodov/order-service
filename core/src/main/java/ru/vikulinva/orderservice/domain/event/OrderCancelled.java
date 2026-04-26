package ru.vikulinva.orderservice.domain.event;

import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.Objects;

/**
 * Заказ отменён покупателем. Внешнее событие, публикуется через Outbox в топик
 * {@code marketplace.orders.v1}.
 *
 * <p>Поле {@code previousStatus} говорит подписчикам, какие компенсации
 * требуются: для DRAFT — никаких, для PENDING_PAYMENT — освободить резерв
 * Inventory, для PAID — запустить refund-сагу через Payment.
 *
 * <p>Подписчики: Inventory, Notification, (опционально) Payment.
 */
public final class OrderCancelled extends DomainEvent {

    private final CustomerId customerId;
    private final SellerId sellerId;
    private final OrderStatus previousStatus;
    private final CancellationReason reason;

    public OrderCancelled(OrderId orderId,
                           CustomerId customerId,
                           SellerId sellerId,
                           OrderStatus previousStatus,
                           CancellationReason reason) {
        super("Order", Objects.requireNonNull(orderId, "orderId").value().toString());
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.previousStatus = Objects.requireNonNull(previousStatus, "previousStatus");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public CustomerId customerId() { return customerId; }
    public SellerId sellerId() { return sellerId; }
    public OrderStatus previousStatus() { return previousStatus; }
    public CancellationReason reason() { return reason; }
}
