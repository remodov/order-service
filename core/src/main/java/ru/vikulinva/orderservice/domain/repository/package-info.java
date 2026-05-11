/**
 * Domain repository-интерфейсы для bounded context {@code order} ({@code OrderRepository}
 * с {@code findByIdForUpdate}). Реализации — в модуле {@code adapter-out-postgres}
 * (jOOQ), генерируются в Ф2 через {@code /ucp-jooq-design}.
 *
 * <p>Интерфейсы наполняются в Ф1 через {@code /ucp-ddd-tactical-design}.
 */
package ru.vikulinva.orderservice.domain.repository;
