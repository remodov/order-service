package ru.vikulinva.orderservice.domain.aggregate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import ru.vikulinva.ddd.AggregateRoot;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.event.DisputeOpened;
import ru.vikulinva.orderservice.domain.event.DisputeResolved;
import ru.vikulinva.orderservice.domain.event.OrderCancelled;
import ru.vikulinva.orderservice.domain.event.OrderCompleted;
import ru.vikulinva.orderservice.domain.event.OrderConfirmed;
import ru.vikulinva.orderservice.domain.event.OrderCreated;
import ru.vikulinva.orderservice.domain.event.OrderDelivered;
import ru.vikulinva.orderservice.domain.event.OrderExpired;
import ru.vikulinva.orderservice.domain.event.OrderItemSnapshot;
import ru.vikulinva.orderservice.domain.event.OrderPaid;
import ru.vikulinva.orderservice.domain.event.OrderPaymentFailed;
import ru.vikulinva.orderservice.domain.event.OrderRefunded;
import ru.vikulinva.orderservice.domain.event.OrderReservationFailed;
import ru.vikulinva.orderservice.domain.event.OrderShipped;
import ru.vikulinva.orderservice.domain.exception.DisputeAlreadyOpenException;
import ru.vikulinva.orderservice.domain.exception.DisputeWindowClosedException;
import ru.vikulinva.orderservice.domain.exception.EmptyOrderException;
import ru.vikulinva.orderservice.domain.exception.MultiSellerNotSupportedException;
import ru.vikulinva.orderservice.domain.exception.OrderBelowMinimumException;
import ru.vikulinva.orderservice.domain.exception.OrderInvalidStateException;
import ru.vikulinva.orderservice.domain.exception.PromoAlreadyAppliedException;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Discount;
import ru.vikulinva.orderservice.domain.valueobject.DisputeDecision;
import ru.vikulinva.orderservice.domain.valueobject.DisputeReason;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.PaymentId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/**
 * Корень агрегата «Заказ». Защищает инварианты: согласованность суммы ({@code BR-001}),
 * один промокод ({@code BR-003}), фиксацию цены позиции после подтверждения ({@code BR-004}),
 * неотрицательность {@code total} ({@code BR-012}), минимальную сумму при подтверждении
 * ({@code BR-013}, 100 ₽), один продавец на заказ ({@code BR-014}). Переходы статусов — строго
 * по матрице {@code docs/spec/aggregates/order.md}; нарушение → {@link OrderInvalidStateException}.
 * Время передаётся параметром {@code Instant now} — домен детерминирован.
 */
public final class Order extends AggregateRoot<OrderId> {

    /** Минимальная сумма заказа для подтверждения ({@code BR-013}). */
    public static final Money MIN_TOTAL = Money.rub(100);
    /** Окно открытия спора после доставки ({@code BR-007}). */
    public static final Duration DISPUTE_WINDOW = Duration.ofDays(14);

    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Discount discount;
    private Money shippingFee;
    private Address shippingAddress;
    private ReservationId reservationId;
    private PaymentId paymentId;
    private Instant paidAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant closedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(OrderId id, CustomerId customerId, OrderStatus status, List<OrderItem> items, Discount discount,
                  Money shippingFee, Address shippingAddress, ReservationId reservationId, PaymentId paymentId,
                  Instant paidAt, Instant shippedAt, Instant deliveredAt, Instant closedAt,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.status = Objects.requireNonNull(status, "status");
        this.items = new ArrayList<>(Objects.requireNonNull(items, "items"));
        this.discount = discount;
        this.shippingFee = Objects.requireNonNull(shippingFee, "shippingFee");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress");
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        this.shippedAt = shippedAt;
        this.deliveredAt = deliveredAt;
        this.closedAt = closedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Новый пустой заказ в статусе {@code DRAFT}. Регистрирует {@link OrderCreated}. */
    public static Order create(OrderId id, CustomerId customerId, Address shippingAddress, Money shippingFee, Instant now) {
        Order order = new Order(id, customerId, OrderStatus.DRAFT, new ArrayList<>(), null,
            shippingFee, shippingAddress, null, null, null, null, null, null, now, now);
        order.registerEvent(new OrderCreated(id, customerId));
        return order;
    }

    /** Восстановление агрегата из хранилища (без регистрации событий). Используется persistence-слоем. */
    public static Order fromPersistence(OrderId id, CustomerId customerId, OrderStatus status, List<OrderItem> items,
                                        Discount discount, Money shippingFee, Address shippingAddress,
                                        ReservationId reservationId, PaymentId paymentId,
                                        Instant paidAt, Instant shippedAt, Instant deliveredAt, Instant closedAt,
                                        Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, status, items, discount, shippingFee, shippingAddress,
            reservationId, paymentId, paidAt, shippedAt, deliveredAt, closedAt, createdAt, updatedAt);
    }

    @Override
    public OrderId getId() {
        return id;
    }

    // --- queries --------------------------------------------------------------------------------

    public CustomerId getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** Неизменяемое представление позиций заказа. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Optional<Discount> getDiscount() {
        return Optional.ofNullable(discount);
    }

    public Money getShippingFee() {
        return shippingFee;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public Optional<ReservationId> getReservationId() {
        return Optional.ofNullable(reservationId);
    }

    public Optional<PaymentId> getPaymentId() {
        return Optional.ofNullable(paymentId);
    }

    public Optional<Instant> getPaidAt() {
        return Optional.ofNullable(paidAt);
    }

    public Optional<Instant> getShippedAt() {
        return Optional.ofNullable(shippedAt);
    }

    public Optional<Instant> getDeliveredAt() {
        return Optional.ofNullable(deliveredAt);
    }

    public Optional<Instant> getClosedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Первый (в V1 — единственный) продавец заказа; {@code null} для пустого заказа. */
    public SellerId primarySellerId() {
        return items.isEmpty() ? null : items.get(0).getSellerId();
    }

    /** Итоговая сумма: {@code sum(item.lineTotal()) + shippingFee − discount} (никогда не отрицательна, {@code BR-001}, {@code BR-012}). */
    public Money total() {
        Money currency = Money.zero(shippingFee.currency());
        Money itemsSum = items.stream().map(OrderItem::lineTotal).reduce(currency, Money::add);
        Money gross = itemsSum.add(shippingFee);
        Money discountAmount = discount == null ? currency : discount.amountFor(gross).min(gross);
        return gross.subtract(discountAmount);
    }

    // --- DRAFT mutations ------------------------------------------------------------------------

    public void addItem(OrderItemId newItemId, ProductId productId, SellerId sellerId, Quantity quantity, Money unitPrice, Instant now) {
        requireStatus(OrderStatus.DRAFT, "add item");
        SellerId existingSeller = primarySellerId();
        if (existingSeller != null && !existingSeller.equals(sellerId)) {
            throw new MultiSellerNotSupportedException(existingSeller, sellerId);
        }
        int index = indexOfItem(productId, sellerId);
        if (index >= 0) {
            items.set(index, items.get(index).withAdditionalQuantity(quantity));
        } else {
            items.add(new OrderItem(newItemId, productId, sellerId, quantity, unitPrice));
        }
        touch(now);
    }

    public void removeItem(ProductId productId, SellerId sellerId, Instant now) {
        requireStatus(OrderStatus.DRAFT, "remove item");
        items.removeIf(item -> item.matches(productId, sellerId));
        touch(now);
    }

    public void applyPromo(Discount discount, Instant now) {
        requireStatus(OrderStatus.DRAFT, "apply promo");
        if (this.discount != null) {
            throw new PromoAlreadyAppliedException(id);
        }
        this.discount = Objects.requireNonNull(discount, "discount");
        touch(now);
    }

    public void removePromo(Instant now) {
        requireStatus(OrderStatus.DRAFT, "remove promo");
        this.discount = null;
        touch(now);
    }

    // --- lifecycle transitions ------------------------------------------------------------------

    /** {@code DRAFT → PENDING_PAYMENT}. Требует ≥1 позиции ({@code BR-002}) и {@code total ≥ 100 RUB} ({@code BR-013}). Регистрирует {@link OrderConfirmed}. */
    public void confirm(Instant now) {
        requireStatus(OrderStatus.DRAFT, "confirm");
        if (items.isEmpty()) {
            throw new EmptyOrderException(id);
        }
        Money currentTotal = total();
        if (currentTotal.isLessThan(MIN_TOTAL)) {
            throw new OrderBelowMinimumException(currentTotal, MIN_TOTAL);
        }
        this.status = OrderStatus.PENDING_PAYMENT;
        touch(now);
        List<OrderItemSnapshot> snapshots = items.stream()
            .map(item -> new OrderItemSnapshot(item.getProductId().value(), item.getSellerId().value(),
                item.getQuantity().value(), item.getUnitPrice().amount()))
            .toList();
        registerEvent(new OrderConfirmed(id, customerId, primarySellerId(), snapshots, currentTotal));
    }

    /** Фиксация id внешнего резерва после {@code ItemReserved} (без смены статуса). */
    public void fixReservation(ReservationId reservationId, Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "fix reservation");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        touch(now);
    }

    /** {@code PENDING_PAYMENT → DRAFT} после {@code ReservationFailed}. Регистрирует {@link OrderReservationFailed}. */
    public void returnToDraftAfterReservationFailure(String reason, Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "handle reservation failure");
        this.status = OrderStatus.DRAFT;
        this.reservationId = null;
        touch(now);
        registerEvent(new OrderReservationFailed(id, customerId, reason));
    }

    /** {@code PENDING_PAYMENT → PAID}. Регистрирует {@link OrderPaid}. */
    public void pay(PaymentId paymentId, Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "pay");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.paidAt = now;
        this.status = OrderStatus.PAID;
        touch(now);
        registerEvent(new OrderPaid(id, paymentId, total(), now));
    }

    /** {@code PENDING_PAYMENT → DRAFT} после {@code PaymentFailed}. Регистрирует {@link OrderPaymentFailed} (несёт id резерва для Inventory). */
    public void returnToDraftAfterPaymentFailure(Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "handle payment failure");
        ReservationId held = this.reservationId;
        this.status = OrderStatus.DRAFT;
        this.reservationId = null;
        touch(now);
        registerEvent(new OrderPaymentFailed(id, customerId, held, now));
    }

    /** {@code PAID → SHIPPED}. Регистрирует {@link OrderShipped}. */
    public void markShipped(String shipmentRef, Instant now) {
        requireStatus(OrderStatus.PAID, "mark shipped");
        if (shipmentRef == null || shipmentRef.isBlank()) {
            throw new IllegalArgumentException("shipmentRef must not be blank");
        }
        this.shippedAt = now;
        this.status = OrderStatus.SHIPPED;
        touch(now);
        registerEvent(new OrderShipped(id, shipmentRef, now));
    }

    /** {@code SHIPPED → DELIVERED}. Стартует окно спора 14 дней. Регистрирует {@link OrderDelivered}. */
    public void confirmDelivery(Instant now) {
        requireStatus(OrderStatus.SHIPPED, "confirm delivery");
        this.deliveredAt = now;
        this.status = OrderStatus.DELIVERED;
        touch(now);
        registerEvent(new OrderDelivered(id, now));
    }

    /** {@code DELIVERED → COMPLETED} (окно спора истекло). Регистрирует {@link OrderCompleted} (Settlement начисляет выручку). */
    public void complete(Instant now) {
        requireStatus(OrderStatus.DELIVERED, "complete");
        this.closedAt = now;
        this.status = OrderStatus.COMPLETED;
        touch(now);
        registerEvent(new OrderCompleted(id, primarySellerId(), total(), now));
    }

    /** {@code PENDING_PAYMENT → EXPIRED} (таймаут оплаты). Регистрирует {@link OrderExpired} (несёт id резерва). */
    public void expire(Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "expire");
        ReservationId held = this.reservationId;
        this.closedAt = now;
        this.status = OrderStatus.EXPIRED;
        this.reservationId = null;
        touch(now);
        registerEvent(new OrderExpired(id, held, now));
    }

    /** {@code DRAFT/PENDING_PAYMENT/PAID/SHIPPED → CANCELLED}. Регистрирует {@link OrderCancelled} (несёт id резерва). Для оплаченных далее запускается saga ProcessRefund → {@code REFUNDED}. */
    public void cancel(Instant now) {
        requireStatusIn(EnumSet.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED), "cancel");
        this.status = OrderStatus.CANCELLED;
        touch(now);
        registerEvent(new OrderCancelled(id, customerId, reservationId, now));
    }

    /** {@code CANCELLED/DISPUTE → REFUNDED} (после saga ProcessRefund). Регистрирует {@link OrderRefunded}. */
    public void markRefunded(Instant now) {
        requireStatusIn(EnumSet.of(OrderStatus.CANCELLED, OrderStatus.DISPUTE), "mark refunded");
        this.closedAt = now;
        this.status = OrderStatus.REFUNDED;
        touch(now);
        registerEvent(new OrderRefunded(id, customerId, primarySellerId(), total(), now));
    }

    /** {@code DELIVERED → DISPUTE}. Только в окне 14 дней ({@code BR-007}). Регистрирует {@link DisputeOpened}. */
    public void openDispute(Instant now, DisputeReason reason) {
        if (status == OrderStatus.DISPUTE) {
            throw new DisputeAlreadyOpenException(id);
        }
        requireStatus(OrderStatus.DELIVERED, "open dispute");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        if (now.isAfter(deliveredAt.plus(DISPUTE_WINDOW))) {
            throw new DisputeWindowClosedException(id, deliveredAt, now);
        }
        this.status = OrderStatus.DISPUTE;
        touch(now);
        registerEvent(new DisputeOpened(id, customerId, primarySellerId(), Objects.requireNonNull(reason, "reason"), now));
    }

    /**
     * {@code DISPUTE → COMPLETED} (решение в пользу продавца — регистрирует {@link DisputeResolved} + {@link OrderCompleted})
     * либо подготовка к возврату (решение в пользу покупателя — регистрирует {@link DisputeResolved}; статус остаётся
     * {@code DISPUTE}, далее saga ProcessRefund вызовет {@link #markRefunded}).
     */
    public void resolveDispute(DisputeDecision decision, Instant now) {
        requireStatus(OrderStatus.DISPUTE, "resolve dispute");
        Objects.requireNonNull(decision, "decision");
        registerEvent(new DisputeResolved(id, decision, now));
        if (decision == DisputeDecision.SELLER) {
            this.closedAt = now;
            this.status = OrderStatus.COMPLETED;
            registerEvent(new OrderCompleted(id, primarySellerId(), total(), now));
        }
        touch(now);
    }

    // --- helpers --------------------------------------------------------------------------------

    private int indexOfItem(ProductId productId, SellerId sellerId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).matches(productId, sellerId)) {
                return i;
            }
        }
        return -1;
    }

    private void requireStatus(OrderStatus expected, String operation) {
        if (status != expected) {
            throw new OrderInvalidStateException(operation, status, expected);
        }
    }

    private void requireStatusIn(Set<OrderStatus> allowed, String operation) {
        if (!allowed.contains(status)) {
            throw new OrderInvalidStateException(operation, status, allowed);
        }
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
