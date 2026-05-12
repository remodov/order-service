package ru.vikulinva.orderservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/**
 * Интеграционный smoke: {@code JooqOrderRepository.save/findById} + запись доменных событий в {@code outbox}
 * с полным JSONB-payload (регрессия {@code BS-16} — событие сериализуется целиком, не только базовые поля).
 * Полноценный базовый класс ({@code DatabasePreparer}/{@code TestObjectGenerator}) — в Ф8 ({@code /ucp-test-design}).
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers(disabledWithoutDocker = true)
class OrderPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void savesAndLoadsOrderWithItemsAndOutbox() {
        OrderId id = OrderId.of(UUID.randomUUID());
        SellerId seller = SellerId.of(UUID.randomUUID());
        Order order = Order.create(id, CustomerId.of(UUID.randomUUID()),
            new Address("RU", "Moscow", "Tverskaya 1", "101000", null), Money.rub(0), Instant.now());
        order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), seller,
            Quantity.of(2), Money.rub(150), Instant.now());
        order.confirm(Instant.now());
        orderRepository.save(order);
        assertThat(order.getEvents()).isEmpty();

        Order loaded = orderRepository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.total()).isEqualTo(Money.rub(300));
        assertThat(loaded.primarySellerId()).isEqualTo(seller);

        var rows = dsl.select(OUTBOX.EVENT_TYPE, OUTBOX.PAYLOAD)
            .from(OUTBOX)
            .where(OUTBOX.AGGREGATE_ID.eq(id.value()))
            .fetch();
        assertThat(rows).extracting(r -> r.get(OUTBOX.EVENT_TYPE))
            .containsExactlyInAnyOrder("OrderCreated", "OrderConfirmed");
        String confirmedPayload = rows.stream()
            .filter(r -> "OrderConfirmed".equals(r.get(OUTBOX.EVENT_TYPE)))
            .map(r -> r.get(OUTBOX.PAYLOAD).data())
            .findFirst()
            .orElseThrow();
        assertThat(confirmedPayload).contains("totalAmount").contains("customerId").contains("aggregateId");
    }

    @Test
    void findByIdForUpdateReturnsOrder() {
        OrderId id = OrderId.of(UUID.randomUUID());
        Order order = Order.create(id, CustomerId.of(UUID.randomUUID()),
            new Address("RU", "Kazan", "Bauman 1", "420000", "PVZ-42"), Money.rub(0), Instant.now());
        order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SellerId.of(UUID.randomUUID()),
            Quantity.of(1), Money.rub(500), Instant.now());
        orderRepository.save(order);

        assertThat(orderRepository.findByIdForUpdate(id)).isPresent()
            .get().extracting(Order::getStatus).isEqualTo(OrderStatus.DRAFT);
        assertThat(orderRepository.findByIdForUpdate(OrderId.of(UUID.randomUUID()))).isEmpty();
    }
}
