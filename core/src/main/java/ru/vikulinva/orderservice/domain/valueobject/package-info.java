/**
 * Value Objects для bounded context {@code order}: {@code Money}, {@code Quantity},
 * {@code Discount} (sealed: {@code PercentageDiscount} / {@code FixedDiscount}),
 * {@code Address}, типизированные id ({@code OrderId}, {@code OrderItemId},
 * {@code CustomerId}, {@code SellerId}, {@code ProductId}, {@code ReservationId},
 * {@code PaymentId}) и enum {@code OrderStatus}.
 *
 * <p>Наполняется в Ф1 через {@code /ucp-ddd-tactical-design} по
 * {@code docs/spec/aggregates/order.md}.
 */
package ru.vikulinva.orderservice.domain.valueobject;
