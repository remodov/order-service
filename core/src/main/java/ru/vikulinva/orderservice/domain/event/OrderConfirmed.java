package ru.vikulinva.orderservice.domain.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/**
 * Заказ подтверждён ({@code DRAFT → PENDING_PAYMENT}). Внешнее событие, топик {@code marketplace.orders.v1}.
 * Inventory резервирует остаток, Notification шлёт уведомление, Read Model обновляется.
 */
@Getter
public final class OrderConfirmed extends DomainEvent {

    private final UUID customerId;
    private final UUID sellerId;
    private final List<OrderItemSnapshot> items;
    private final BigDecimal totalAmount;
    private final String currency;

    public OrderConfirmed(OrderId orderId, CustomerId customerId, SellerId sellerId,
                          List<OrderItemSnapshot> items, Money total) {
        super("Order", orderId.value().toString());
        this.customerId = customerId.value();
        this.sellerId = sellerId.value();
        this.items = List.copyOf(items);
        this.totalAmount = total.amount();
        this.currency = total.currency().getCurrencyCode();
    }
}
