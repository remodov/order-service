/**
 * Domain events для bounded context {@code order} ({@code OrderCreated},
 * {@code OrderConfirmed}, {@code OrderPaid}, {@code OrderShipped}, …). Регистрируются
 * в агрегате {@code Order} через {@code registerEvent(...)}, доставляются в Outbox
 * в той же транзакции, публикуются в Kafka {@code marketplace.orders.v1}.
 *
 * <p>Наполняется в Ф1 через {@code /ucp-ddd-tactical-design} по
 * {@code docs/spec/aggregates/order.md}.
 */
package ru.vikulinva.orderservice.domain.event;
