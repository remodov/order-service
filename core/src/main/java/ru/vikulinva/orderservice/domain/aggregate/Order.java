package ru.vikulinva.orderservice.domain.aggregate;

import ru.vikulinva.ddd.AggregateRoot;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.event.OrderConfirmed;
import ru.vikulinva.orderservice.domain.event.OrderCreated;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Discount;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Корень агрегата «Заказ». Защищает инварианты:
 *
 * <ul>
 *   <li>BR-001: {@code total = sum(items.lineTotal) − discount + shippingFee}.</li>
 *   <li>BR-014: один заказ — один продавец (V1).</li>
 *   <li>BR-013: минимальная сумма для confirm — 100 RUB (проверяется в {@code confirm()}, не здесь).</li>
 * </ul>
 *
 * Создание идёт через фабричный метод {@link #create(OrderId, CustomerId, List, Money, Address)}
 * — внутри регистрирует событие {@link OrderCreated}.
 * Перевод состояния (confirm/pay/ship/...) — отдельные методы, добавляются по мере UC.
 */
public final class Order extends AggregateRoot<OrderId> {

    private final OrderId id;
    private final CustomerId customerId;
    private final SellerId sellerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Discount discount;
    private final Money shippingFee;
    private final Address shippingAddress;
    private final Instant createdAt;

    private Order(OrderId id,
                   CustomerId customerId,
                   SellerId sellerId,
                   OrderStatus status,
                   List<OrderItem> items,
                   Discount discount,
                   Money shippingFee,
                   Address shippingAddress,
                   Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Order.id");
        this.customerId = Objects.requireNonNull(customerId, "Order.customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "Order.sellerId");
        this.status = Objects.requireNonNull(status, "Order.status");
        this.items = new ArrayList<>(Objects.requireNonNull(items, "Order.items"));
        this.discount = discount;
        this.shippingFee = Objects.requireNonNull(shippingFee, "Order.shippingFee");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "Order.shippingAddress");
        this.createdAt = Objects.requireNonNull(createdAt, "Order.createdAt");
    }

    /**
     * Создать новый заказ в статусе {@link OrderStatus#DRAFT} и зарегистрировать
     * событие {@link OrderCreated}.
     *
     * <p>Инварианты:
     * <ul>
     *   <li>≥ 1 позиции;</li>
     *   <li>BR-014: все позиции от одного продавца.</li>
     * </ul>
     */
    public static Order create(OrderId id,
                                CustomerId customerId,
                                List<OrderItem> items,
                                Money shippingFee,
                                Address shippingAddress,
                                Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order requires at least one item");
        }
        SellerId seller = items.get(0).sellerId();
        boolean multiSeller = items.stream().anyMatch(i -> !i.sellerId().equals(seller));
        if (multiSeller) {
            throw new IllegalArgumentException("BR-014: order must contain items from a single seller");
        }

        Order order = new Order(id, customerId, seller, OrderStatus.DRAFT,
            new ArrayList<>(items), null, shippingFee, shippingAddress, createdAt);

        List<OrderCreated.ItemSnapshot> snapshots = items.stream()
            .map(i -> new OrderCreated.ItemSnapshot(
                i.productId().value(), i.quantity().value(), i.unitPrice()))
            .toList();
        order.registerEvent(new OrderCreated(id, customerId, seller, order.total(), snapshots));
        return order;
    }

    /**
     * Restore-конструктор для репозитория (без публикации событий).
     */
    public static Order restore(OrderId id,
                                  CustomerId customerId,
                                  SellerId sellerId,
                                  OrderStatus status,
                                  List<OrderItem> items,
                                  Discount discount,
                                  Money shippingFee,
                                  Address shippingAddress,
                                  Instant createdAt) {
        return new Order(id, customerId, sellerId, status, items, discount,
            shippingFee, shippingAddress, createdAt);
    }

    @Override
    public OrderId getId() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public SellerId sellerId() {
        return sellerId;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    public Discount discount() {
        return discount;
    }

    public Money shippingFee() {
        return shippingFee;
    }

    public Address shippingAddress() {
        return shippingAddress;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Подтверждение заказа: {@code DRAFT → PENDING_PAYMENT}. Регистрирует
     * {@link OrderConfirmed}.
     *
     * <p>Инварианты:
     * <ul>
     *   <li>Текущий статус — {@link OrderStatus#DRAFT}, иначе {@code IllegalStateException}.</li>
     *   <li>BR-013: сумма заказа ≥ {@link #MIN_CONFIRM_AMOUNT_RUB} ₽.</li>
     *   <li>BR-002: ≥ 1 позиции (гарантировано {@link #create}).</li>
     * </ul>
     */
    public void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Order can only be confirmed from DRAFT, current status: " + status);
        }
        Money current = total();
        if (current.amount().compareTo(MIN_CONFIRM_AMOUNT_RUB) < 0) {
            throw new IllegalStateException(
                "BR-013: order total %s is below minimum %s".formatted(current, MIN_CONFIRM_AMOUNT_RUB));
        }
        this.status = OrderStatus.PENDING_PAYMENT;
        List<OrderConfirmed.ItemSnapshot> snapshots = items.stream()
            .map(i -> new OrderConfirmed.ItemSnapshot(
                i.productId().value(), i.quantity().value(), i.unitPrice()))
            .toList();
        registerEvent(new OrderConfirmed(id, customerId, sellerId, snapshots, current));
    }

    /** BR-013: минимальная сумма заказа для confirm. */
    private static final java.math.BigDecimal MIN_CONFIRM_AMOUNT_RUB = new java.math.BigDecimal("100.00");

    /**
     * BR-001: сумма заказа = сумма позиций − скидка + доставка.
     */
    public Money total() {
        Money itemsSum = items.stream()
            .map(OrderItem::lineTotal)
            .reduce(Money::add)
            .orElseThrow(() -> new IllegalStateException("Order has no items"));
        Money afterDiscount = (discount == null) ? itemsSum : discount.applyTo(itemsSum);
        return afterDiscount.add(shippingFee);
    }
}
