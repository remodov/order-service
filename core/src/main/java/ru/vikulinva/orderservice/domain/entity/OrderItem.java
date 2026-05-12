package ru.vikulinva.orderservice.domain.entity;

import java.util.Objects;
import ru.vikulinva.ddd.Entity;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/**
 * Позиция заказа — внутренняя сущность агрегата {@code Order}. Уникальна в рамках агрегата по
 * паре {@code (productId, sellerId)}. {@code unitPrice} фиксируется на момент оформления и не
 * меняется ({@code BR-004}). Immutable: повторное добавление того же товара агрегат отражает
 * созданием новой позиции с тем же {@code id} и увеличенным {@code quantity}. Equals/hashCode —
 * по {@code id} (наследуются из {@link Entity}).
 */
public final class OrderItem extends Entity<OrderItemId> {

    private final OrderItemId id;
    private final ProductId productId;
    private final SellerId sellerId;
    private final Quantity quantity;
    private final Money unitPrice;

    public OrderItem(OrderItemId id, ProductId productId, SellerId sellerId, Quantity quantity, Money unitPrice) {
        this.id = Objects.requireNonNull(id, "id");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    }

    @Override
    public OrderItemId getId() {
        return id;
    }

    public ProductId getProductId() {
        return productId;
    }

    public SellerId getSellerId() {
        return sellerId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    /** Сумма позиции: {@code unitPrice × quantity}. */
    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }

    public boolean matches(ProductId productId, SellerId sellerId) {
        return this.productId.equals(productId) && this.sellerId.equals(sellerId);
    }

    /** Копия позиции с увеличенным количеством (для повторного добавления того же товара). */
    public OrderItem withAdditionalQuantity(Quantity extra) {
        return new OrderItem(id, productId, sellerId, quantity.plus(extra), unitPrice);
    }
}
