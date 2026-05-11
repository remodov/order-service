/**
 * Outbound ports — что {@code core} нужно от внешнего мира: {@code CatalogPort}
 * (валидация цен/промокодов), {@code PaymentPort} (запуск платежа / refund),
 * {@code ExternalEventPublisher} (публикация saga-команд). Интерфейсы здесь —
 * реализации в {@code adapter-out-*} модулях.
 *
 * <p>Наполняется по мере фаз: {@code CatalogPort} — Ф3, {@code PaymentPort} — Ф4/Ф6,
 * через {@code /ucp-integration-design}.
 */
package ru.vikulinva.orderservice.port.out;
