/**
 * Command-use cases (inbound) для bounded context {@code order}: {@code CreateOrder},
 * {@code AddItem} / {@code RemoveItem}, {@code ApplyPromo} / {@code RemovePromo},
 * {@code ConfirmOrder}, {@code MarkShipped}, {@code ConfirmDelivery}, {@code CancelOrder},
 * {@code OpenDispute}, {@code ResolveDispute}, плюс event-handler'ы ({@code HandleItemReserved},
 * {@code HandlePaymentSucceeded}, …). Каждая — {@code UseCaseCommand<R>} + {@code UseCaseHandler}.
 *
 * <p>Наполняется в Ф3+ через {@code /ucp-pattern-design} по
 * {@code docs/spec/07-order-service-commands.md}.
 */
package ru.vikulinva.orderservice.usecase.command;
