/**
 * Aggregate Roots для bounded context {@code order}. Корень — {@code Order}:
 * rich domain methods ({@code confirm()}, {@code pay()}, {@code ship()}, …),
 * порождает domain events, гарантирует инварианты ({@code BR-001}, {@code BR-003},
 * {@code BR-004}, {@code BR-012}, {@code BR-013}, {@code BR-014}).
 *
 * <p>Наполняется в Ф1 через {@code /ucp-ddd-tactical-design} по
 * {@code docs/spec/03-order-service-model.md} и {@code docs/spec/04-order-service-lifecycle.md}.
 */
package ru.vikulinva.orderservice.domain.aggregate;
