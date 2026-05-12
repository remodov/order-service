package ru.vikulinva.orderservice.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.domain.event.DisputeOpened;
import ru.vikulinva.orderservice.domain.event.DisputeResolved;
import ru.vikulinva.orderservice.domain.event.OrderCancelled;
import ru.vikulinva.orderservice.domain.event.OrderCompleted;
import ru.vikulinva.orderservice.domain.event.OrderConfirmed;
import ru.vikulinva.orderservice.domain.event.OrderCreated;
import ru.vikulinva.orderservice.domain.event.OrderDelivered;
import ru.vikulinva.orderservice.domain.event.OrderExpired;
import ru.vikulinva.orderservice.domain.event.OrderPaid;
import ru.vikulinva.orderservice.domain.event.OrderRefunded;
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
import ru.vikulinva.orderservice.domain.valueobject.DisputeDecision;
import ru.vikulinva.orderservice.domain.valueobject.DisputeReason;
import ru.vikulinva.orderservice.domain.valueobject.FixedDiscount;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.PaymentId;
import ru.vikulinva.orderservice.domain.valueobject.PercentageDiscount;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

class OrderTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "101000", null);
    private static final SellerId SELLER = SellerId.of(UUID.randomUUID());

    private Order draftOrder() {
        return Order.create(OrderId.of(UUID.randomUUID()), CustomerId.of(UUID.randomUUID()),
            ADDRESS, Money.rub(0), T0);
    }

    private Order draftWith(long unitPriceRub, int qty) {
        Order order = draftOrder();
        order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER,
            Quantity.of(qty), Money.rub(unitPriceRub), T0);
        return order;
    }

    private Order paidOrder() {
        Order order = draftWith(200, 1);
        order.confirm(T0);
        order.pay(PaymentId.of(UUID.randomUUID()), T0);
        order.clearDomainEvents();
        return order;
    }

    @SuppressWarnings("unchecked")
    private static <T extends DomainEvent> T lastEvent(Order order, Class<T> type) {
        assertThat(order.getEvents()).last().isInstanceOf(type);
        return (T) order.getEvents().get(order.getEvents().size() - 1);
    }

    // --- creation & totals (BR-001, BR-012) ----------------------------------------------------

    @Test
    void createStartsInDraftAndRegistersEvent() {
        Order order = draftOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.total()).isEqualTo(Money.rub(0));
        assertThat(order.getEvents()).singleElement().isInstanceOf(OrderCreated.class);
    }

    @Test
    void totalIsItemsSumPlusShippingMinusDiscount() {
        Order order = Order.create(OrderId.of(UUID.randomUUID()), CustomerId.of(UUID.randomUUID()),
            ADDRESS, Money.rub(50), T0);
        order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER, Quantity.of(2), Money.rub(100), T0);
        assertThat(order.total()).isEqualTo(Money.rub(250));
        order.applyPromo(PercentageDiscount.of(BigDecimal.valueOf(10)), T0);
        assertThat(order.total()).isEqualTo(Money.rub(225));
    }

    @Test
    void discountLargerThanBaseClampsTotalToZero() {
        Order order = draftWith(100, 1);
        order.applyPromo(FixedDiscount.of(Money.rub(1000)), T0);
        assertThat(order.total()).isEqualTo(Money.rub(0));
    }

    @Test
    void mergesDuplicateItemQuantity() {
        Order order = draftOrder();
        ProductId product = ProductId.of(UUID.randomUUID());
        order.addItem(OrderItemId.of(UUID.randomUUID()), product, SELLER, Quantity.of(1), Money.rub(100), T0);
        order.addItem(OrderItemId.of(UUID.randomUUID()), product, SELLER, Quantity.of(2), Money.rub(100), T0);
        assertThat(order.getItems()).singleElement()
            .extracting(item -> item.getQuantity().value()).isEqualTo(3);
        assertThat(order.total()).isEqualTo(Money.rub(300));
    }

    // --- DRAFT-only mutations (BR-004) & multi-seller (BR-014) & promo (BR-003) -----------------

    @Test
    void rejectsItemFromAnotherSeller() {
        Order order = draftWith(100, 1);
        assertThatThrownBy(() -> order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()),
            SellerId.of(UUID.randomUUID()), Quantity.of(1), Money.rub(100), T0))
            .isInstanceOf(MultiSellerNotSupportedException.class);
    }

    @Test
    void rejectsSecondPromo() {
        Order order = draftWith(100, 1);
        order.applyPromo(FixedDiscount.of(Money.rub(10)), T0);
        assertThatThrownBy(() -> order.applyPromo(FixedDiscount.of(Money.rub(20)), T0))
            .isInstanceOf(PromoAlreadyAppliedException.class);
        order.removePromo(T0);
        order.applyPromo(FixedDiscount.of(Money.rub(20)), T0);
        assertThat(order.total()).isEqualTo(Money.rub(80));
    }

    @Test
    void cannotMutateItemsAfterConfirm() {
        Order order = draftWith(200, 1);
        order.confirm(T0);
        assertThatThrownBy(() -> order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()),
            SELLER, Quantity.of(1), Money.rub(100), T0)).isInstanceOf(OrderInvalidStateException.class);
        assertThatThrownBy(() -> order.applyPromo(FixedDiscount.of(Money.rub(10)), T0)).isInstanceOf(OrderInvalidStateException.class);
    }

    // --- confirm (BR-002 empty, BR-013 minimum) ------------------------------------------------

    @Test
    void confirmRejectsEmptyOrder() {
        Order order = draftOrder();
        assertThatThrownBy(() -> order.confirm(T0)).isInstanceOf(EmptyOrderException.class);
    }

    @Test
    void confirmRejectsBelowMinimum() {
        Order order = draftWith(50, 1);
        assertThatThrownBy(() -> order.confirm(T0)).isInstanceOf(OrderBelowMinimumException.class);
    }

    @Test
    void confirmAtExactMinimumIsAllowed() {
        Order order = draftWith(100, 1);
        order.confirm(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void confirmEmitsOrderConfirmedWithTotalAndSeller() {
        Order order = draftWith(200, 2);
        order.clearDomainEvents();
        order.confirm(T0);
        OrderConfirmed event = lastEvent(order, OrderConfirmed.class);
        assertThat(event.getTotalAmount()).isEqualByComparingTo("400.00");
        assertThat(event.getSellerId()).isEqualTo(SELLER.value());
        assertThat(event.getItems()).hasSize(1);
    }

    // --- happy path lifecycle (§4 matrix) ------------------------------------------------------

    @Test
    void fullHappyPath() {
        Order order = draftWith(500, 1);
        order.confirm(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        ReservationId reservation = ReservationId.of(UUID.randomUUID());
        order.fixReservation(reservation, T0);
        assertThat(order.getReservationId()).contains(reservation);

        order.clearDomainEvents();
        PaymentId payment = PaymentId.of(UUID.randomUUID());
        order.pay(payment, T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).contains(payment);
        OrderPaid paid = lastEvent(order, OrderPaid.class);
        assertThat(paid.getAmount()).isEqualByComparingTo("500.00");

        order.clearDomainEvents();
        order.markShipped("CDEK-12345", T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(lastEvent(order, OrderShipped.class).getShipmentRef()).isEqualTo("CDEK-12345");

        order.clearDomainEvents();
        Instant deliveredAt = T0.plusSeconds(86400);
        order.confirmDelivery(deliveredAt);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(lastEvent(order, OrderDelivered.class).getDeliveredAt()).isEqualTo(deliveredAt);

        order.clearDomainEvents();
        Instant closedAt = deliveredAt.plus(java.time.Duration.ofDays(15));
        order.complete(closedAt);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus().isTerminal()).isTrue();
        OrderCompleted completed = lastEvent(order, OrderCompleted.class);
        assertThat(completed.getSellerId()).isEqualTo(SELLER.value());
        assertThat(completed.getTotalAmount()).isEqualByComparingTo("500.00");
    }

    // --- alternative flows ---------------------------------------------------------------------

    @Test
    void paymentFailedReturnsToDraftAndKeepsReservationInEvent() {
        Order order = draftWith(300, 1);
        order.confirm(T0);
        ReservationId reservation = ReservationId.of(UUID.randomUUID());
        order.fixReservation(reservation, T0);
        order.clearDomainEvents();
        order.returnToDraftAfterPaymentFailure(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getReservationId()).isEmpty();
        assertThat(order.getEvents()).last().isInstanceOf(ru.vikulinva.orderservice.domain.event.OrderPaymentFailed.class);
    }

    @Test
    void reservationFailedReturnsToDraft() {
        Order order = draftWith(300, 1);
        order.confirm(T0);
        order.clearDomainEvents();
        order.returnToDraftAfterReservationFailure("out of stock: SKU-1", T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getEvents()).last().isInstanceOf(ru.vikulinva.orderservice.domain.event.OrderReservationFailed.class);
    }

    @Test
    void expireFromPendingPayment() {
        Order order = draftWith(300, 1);
        order.confirm(T0);
        order.clearDomainEvents();
        order.expire(T0.plus(java.time.Duration.ofMinutes(16)));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getStatus().isTerminal()).isTrue();
        assertThat(order.getEvents()).last().isInstanceOf(OrderExpired.class);
    }

    @Test
    void cancelFromPaidGoesToCancelled() {
        Order order = paidOrder();
        order.cancel(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getEvents()).last().isInstanceOf(OrderCancelled.class);
        order.clearDomainEvents();
        order.markRefunded(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getStatus().isTerminal()).isTrue();
        assertThat(order.getEvents()).last().isInstanceOf(OrderRefunded.class);
    }

    @Test
    void cancelFromDraft() {
        Order order = draftWith(300, 1);
        order.cancel(T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // --- disputes (BR-007) ---------------------------------------------------------------------

    private Order deliveredOrder(Instant deliveredAt) {
        Order order = paidOrder();
        order.markShipped("CDEK-1", deliveredAt.minusSeconds(3600));
        order.confirmDelivery(deliveredAt);
        order.clearDomainEvents();
        return order;
    }

    @Test
    void openDisputeWithinWindow() {
        Instant deliveredAt = T0;
        Order order = deliveredOrder(deliveredAt);
        order.openDispute(deliveredAt.plus(java.time.Duration.ofDays(13)), DisputeReason.of("damaged"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DISPUTE);
        assertThat(order.getEvents()).last().isInstanceOf(DisputeOpened.class);
    }

    @Test
    void openDisputeAfterWindowRejected() {
        Instant deliveredAt = T0;
        Order order = deliveredOrder(deliveredAt);
        assertThatThrownBy(() -> order.openDispute(deliveredAt.plus(java.time.Duration.ofDays(15)), DisputeReason.of("late")))
            .isInstanceOf(DisputeWindowClosedException.class);
    }

    @Test
    void openDisputeTwiceRejected() {
        Instant deliveredAt = T0;
        Order order = deliveredOrder(deliveredAt);
        order.openDispute(deliveredAt.plusSeconds(3600), DisputeReason.of("damaged"));
        assertThatThrownBy(() -> order.openDispute(deliveredAt.plusSeconds(7200), DisputeReason.of("again")))
            .isInstanceOf(DisputeAlreadyOpenException.class);
    }

    @Test
    void resolveDisputeForBuyerStaysInDisputeThenRefunded() {
        Instant deliveredAt = T0;
        Order order = deliveredOrder(deliveredAt);
        order.openDispute(deliveredAt.plusSeconds(3600), DisputeReason.of("damaged"));
        order.clearDomainEvents();
        order.resolveDispute(DisputeDecision.BUYER, deliveredAt.plusSeconds(7200));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DISPUTE);
        assertThat(order.getEvents()).singleElement().isInstanceOf(DisputeResolved.class);
        order.markRefunded(deliveredAt.plusSeconds(8000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void resolveDisputeForSellerCompletes() {
        Instant deliveredAt = T0;
        Order order = deliveredOrder(deliveredAt);
        order.openDispute(deliveredAt.plusSeconds(3600), DisputeReason.of("damaged"));
        order.clearDomainEvents();
        order.resolveDispute(DisputeDecision.SELLER, deliveredAt.plusSeconds(7200));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getEvents()).hasSize(2)
            .anySatisfy(e -> assertThat(e).isInstanceOf(DisputeResolved.class))
            .anySatisfy(e -> assertThat(e).isInstanceOf(OrderCompleted.class));
    }

    // --- invalid transitions -------------------------------------------------------------------

    @Nested
    class InvalidTransitions {

        @Test
        void payFromDraftRejected() {
            Order order = draftWith(200, 1);
            assertThatThrownBy(() -> order.pay(PaymentId.of(UUID.randomUUID()), T0)).isInstanceOf(OrderInvalidStateException.class);
        }

        @Test
        void markShippedFromDraftRejected() {
            Order order = draftWith(200, 1);
            assertThatThrownBy(() -> order.markShipped("ref", T0)).isInstanceOf(OrderInvalidStateException.class);
        }

        @Test
        void confirmTwiceRejected() {
            Order order = draftWith(200, 1);
            order.confirm(T0);
            assertThatThrownBy(() -> order.confirm(T0)).isInstanceOf(OrderInvalidStateException.class);
        }

        @Test
        void cancelFromCompletedRejected() {
            Order order = paidOrder();
            order.markShipped("ref", T0);
            order.confirmDelivery(T0);
            order.complete(T0);
            assertThatThrownBy(() -> order.cancel(T0)).isInstanceOf(OrderInvalidStateException.class);
        }

        @Test
        void openDisputeFromPaidRejected() {
            Order order = paidOrder();
            assertThatThrownBy(() -> order.openDispute(T0, DisputeReason.of("x"))).isInstanceOf(OrderInvalidStateException.class);
        }

        @Test
        void anyTransitionFromExpiredRejected() {
            Order order = draftWith(200, 1);
            order.confirm(T0);
            order.expire(T0);
            assertThatThrownBy(() -> order.cancel(T0)).isInstanceOf(OrderInvalidStateException.class);
            assertThatThrownBy(() -> order.pay(PaymentId.of(UUID.randomUUID()), T0)).isInstanceOf(OrderInvalidStateException.class);
        }
    }
}
