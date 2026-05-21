/**
 * Query-use cases (inbound, read-side) для bounded context {@code order}:
 * {@code GetOrderById}, {@code ListMyOrders}, {@code ListSellerOrders},
 * {@code GetOrderTimeline}. Каждая — {@code UseCaseQuery<R>} + {@code UseCaseHandler},
 * читает Read Model ({@code order_summaries}, {@code order_timelines}) или write-side
 * (для read-your-own-writes).
 *
 * <p>Наполняется в Ф5 через {@code /ucp-cqrs-design} по
 * {@code docs/spec/aggregates/order.md}.
 */
package ru.vikulinva.orderservice.usecase.query;
