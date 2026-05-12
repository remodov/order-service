package ru.vikulinva.orderservice.adapter.out.postgres.repository;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDERS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.ORDER_ITEMS;
import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import ru.vikulinva.ddd.DomainEvent;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrderItemsPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.mapper.EventPayloadSerializer;
import ru.vikulinva.orderservice.adapter.out.postgres.mapper.OrderDomainRecordMapper;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.repository.OrderRepository;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;

/**
 * jOOQ-реализация {@link OrderRepository}. {@code save} в одной (вызывающей) транзакции пишет агрегат
 * (orders + order_items) и накопленные доменные события в {@code outbox}, затем очищает события агрегата
 * ({@code BR-015} — атомарность события и записи). Управление транзакцией — на стороне UseCaseHandler (Ф3+),
 * не здесь.
 */
@Repository
@RequiredArgsConstructor
public class JooqOrderRepository implements OrderRepository {

    private static final String AGGREGATE_TYPE = "Order";

    private final DSLContext dsl;
    private final OrderDomainRecordMapper mapper;
    private final EventPayloadSerializer eventPayloadSerializer;

    @Override
    public Optional<Order> findById(OrderId id) {
        return load(id, false);
    }

    @Override
    public Optional<Order> findByIdForUpdate(OrderId id) {
        return load(id, true);
    }

    @Override
    public Order save(Order order) {
        UUID orderId = order.getId().value();
        OrdersPojo header = mapper.toOrdersPojo(order);
        var headerRecord = dsl.newRecord(ORDERS, header);
        dsl.insertInto(ORDERS).set(headerRecord).onDuplicateKeyUpdate().set(headerRecord).execute();

        dsl.deleteFrom(ORDER_ITEMS).where(ORDER_ITEMS.ORDER_ID.eq(orderId)).execute();
        for (OrderItemsPojo itemPojo : mapper.toOrderItemsPojos(order)) {
            dsl.insertInto(ORDER_ITEMS).set(dsl.newRecord(ORDER_ITEMS, itemPojo)).execute();
        }

        for (DomainEvent event : order.getEvents()) {
            dsl.insertInto(OUTBOX)
                .set(OUTBOX.ID, UUID.randomUUID())
                .set(OUTBOX.AGGREGATE_ID, orderId)
                .set(OUTBOX.AGGREGATE_TYPE, AGGREGATE_TYPE)
                .set(OUTBOX.EVENT_TYPE, event.getClass().getSimpleName())
                .set(OUTBOX.PAYLOAD, JSONB.valueOf(eventPayloadSerializer.toJson(event)))
                .set(OUTBOX.OCCURRED_AT, event.getCreatedAt())
                .execute();
        }
        order.clearDomainEvents();
        return order;
    }

    @Override
    public void delete(Order order) {
        dsl.deleteFrom(ORDERS).where(ORDERS.ID.eq(order.getId().value())).execute();
    }

    private Optional<Order> load(OrderId id, boolean forUpdate) {
        var step = dsl.selectFrom(ORDERS).where(ORDERS.ID.eq(id.value()));
        var record = forUpdate ? step.forUpdate().fetchOne() : step.fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        OrdersPojo header = record.into(OrdersPojo.class);
        List<OrderItemsPojo> items = dsl.selectFrom(ORDER_ITEMS)
            .where(ORDER_ITEMS.ORDER_ID.eq(id.value()))
            .fetchInto(OrderItemsPojo.class);
        return Optional.of(mapper.toDomain(header, items));
    }
}
