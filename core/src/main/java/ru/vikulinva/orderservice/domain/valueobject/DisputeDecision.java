package ru.vikulinva.orderservice.domain.valueobject;

/** Решение оператора по спору: в пользу покупателя ({@link #BUYER}) или продавца ({@link #SELLER}). */
public enum DisputeDecision {
    BUYER,
    SELLER
}
