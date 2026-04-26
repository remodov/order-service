package ru.vikulinva.orderservice.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.event.OrderConfirmed;
import ru.vikulinva.orderservice.domain.event.OrderCreated;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты на агрегат {@link Order}. Без Spring (TS-26).
 */
class OrderTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Test
    @DisplayName("create: переводит заказ в DRAFT и регистрирует OrderCreated")
    void create_setsDraftAndEmitsEvent() {
        var orderId = OrderId.of(UUID.randomUUID());
        var customer = CustomerId.of(UUID.randomUUID());
        var seller = SellerId.of(UUID.randomUUID());
        var item = item(seller, "1000.00");

        var order = Order.create(orderId, customer, List.of(item),
            Money.zero(Money.RUB), ADDRESS, Instant.parse("2026-04-01T10:00:00Z"));

        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getEvents()).hasSize(1).first().isInstanceOf(OrderCreated.class);
        var event = (OrderCreated) order.getEvents().get(0);
        assertThat(event.customerId()).isEqualTo(customer);
        assertThat(event.sellerId()).isEqualTo(seller);
        assertThat(event.itemsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-001: total = sum(items) − discount + shippingFee")
    void total_followsBR001() {
        var seller = SellerId.of(UUID.randomUUID());
        var items = List.of(
            item(seller, "100.00"),
            item(seller, "250.00")
        );

        var order = Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()), items,
            Money.rub(50), ADDRESS, Instant.now());

        // 100 + 250 = 350; without discount; +50 shipping = 400
        assertThat(order.total()).isEqualTo(Money.rub("400.00"));
    }

    @Test
    @DisplayName("BR-014: множественный продавец отклоняется")
    void multiSeller_isRejected() {
        var sellerA = SellerId.of(UUID.randomUUID());
        var sellerB = SellerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(sellerA, "100.00"), item(sellerB, "200.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BR-014");
    }

    @Test
    @DisplayName("создание без позиций отклоняется")
    void empty_isRejected() {
        assertThatThrownBy(() -> Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(),
            Money.zero(Money.RUB), ADDRESS, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("confirm: DRAFT → PENDING_PAYMENT и регистрирует OrderConfirmed")
    void confirm_movesDraftToPendingPaymentAndEmitsEvent() {
        var order = sampleOrder();
        order.clearDomainEvents();

        order.confirm();

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getEvents()).hasSize(1).first().isInstanceOf(OrderConfirmed.class);
        var event = (OrderConfirmed) order.getEvents().get(0);
        assertThat(event.total()).isEqualTo(order.total());
    }

    @Test
    @DisplayName("confirm: повторный вызов в PENDING_PAYMENT — IllegalStateException")
    void confirm_fromNonDraft_throws() {
        var order = sampleOrder();
        order.confirm();

        assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("BR-013: confirm с суммой ниже 100 RUB отклоняется")
    void confirm_belowMinimum_throws() {
        var seller = SellerId.of(UUID.randomUUID());
        var order = Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(seller, "50.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now());

        assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BR-013");
    }

    @Test
    @DisplayName("clearDomainEvents очищает зарегистрированные события")
    void clearDomainEvents_works() {
        var order = sampleOrder();
        assertThat(order.getEvents()).hasSize(1);

        order.clearDomainEvents();

        assertThat(order.getEvents()).isEmpty();
    }

    private OrderItem item(SellerId seller, String price) {
        return new OrderItem(
            OrderItemId.of(UUID.randomUUID()),
            ProductId.of(UUID.randomUUID()),
            seller,
            Quantity.of(1),
            Money.rub(price)
        );
    }

    private Order sampleOrder() {
        var seller = SellerId.of(UUID.randomUUID());
        return Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(seller, "100.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now());
    }
}
