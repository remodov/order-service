package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

/**
 * Скидка на заказ. Sealed: либо процент от суммы позиций ({@link PercentageDiscount}),
 * либо фиксированная сумма ({@link FixedDiscount}). В обоих случаях итоговая применённая
 * сумма обрезается до базы заказа — отрицательного {@code total} быть не может ({@code BR-012}).
 */
public sealed interface Discount extends ValueObject permits PercentageDiscount, FixedDiscount {

    /**
     * Сколько денег скидка снимает с заказа, учитывая базу {@code orderBase}
     * (= сумма позиций + доставка). Никогда не больше базы.
     */
    Money amountFor(Money orderBase);
}
