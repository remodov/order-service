package ru.vikulinva.orderservice.adapter.out.postgres.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrderItemsPojo;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.pojos.OrdersPojo;
import ru.vikulinva.orderservice.domain.aggregate.Order;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Discount;
import ru.vikulinva.orderservice.domain.valueobject.FixedDiscount;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.PaymentId;
import ru.vikulinva.orderservice.domain.valueobject.PercentageDiscount;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.ReservationId;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

/**
 * Маппинг между jOOQ-POJO ({@code OrdersPojo} / {@code OrderItemsPojo} / generated enum {@code OrderStatus})
 * и доменной моделью ({@code Order} / {@code OrderItem} / VO). Plain Java — слишком много кастомной логики
 * (sealed {@code Discount}, {@code Money}+currency, типизированные id, {@code Instant ↔ OffsetDateTime}).
 */
@Component
public class OrderDomainRecordMapper {

    private static final String DISCOUNT_PERCENTAGE = "PERCENTAGE";
    private static final String DISCOUNT_FIXED = "FIXED";

    public Order toDomain(OrdersPojo header, List<OrderItemsPojo> items) {
        Currency currency = Currency.getInstance(header.getCurrency());
        List<OrderItem> domainItems = items.stream().map(this::toDomainItem).toList();
        return Order.fromPersistence(
            OrderId.of(header.getId()),
            CustomerId.of(header.getCustomerId()),
            ru.vikulinva.orderservice.domain.valueobject.OrderStatus.valueOf(header.getStatus().name()),
            domainItems,
            toDiscount(header.getDiscountKind(), header.getDiscountPercentage(), header.getDiscountFixedAmount()),
            Money.of(header.getShippingFee(), currency),
            new Address(header.getShippingCountry(), header.getShippingCity(), header.getShippingStreet(),
                header.getShippingPostalCode(), header.getShippingPickupPointCode()),
            header.getReservationId() == null ? null : ReservationId.of(header.getReservationId()),
            header.getPaymentId() == null ? null : PaymentId.of(header.getPaymentId()),
            toInstant(header.getPaidAt()), toInstant(header.getShippedAt()),
            toInstant(header.getDeliveredAt()), toInstant(header.getClosedAt()),
            toInstant(header.getCreatedAt()), toInstant(header.getUpdatedAt()));
    }

    public OrdersPojo toOrdersPojo(Order order) {
        Money total = order.total();
        OrdersPojo pojo = new OrdersPojo();
        pojo.setId(order.getId().value());
        pojo.setCustomerId(order.getCustomerId().value());
        pojo.setStatus(ru.vikulinva.orderservice.adapter.out.postgres.generated.enums.OrderStatus.valueOf(order.getStatus().name()));
        pojo.setCurrency(total.currency().getCurrencyCode());
        pojo.setTotalAmount(total.amount());
        pojo.setShippingFee(order.getShippingFee().amount());
        order.getDiscount().ifPresent(discount -> applyDiscount(pojo, discount));
        Address address = order.getShippingAddress();
        pojo.setShippingCountry(address.country());
        pojo.setShippingCity(address.city());
        pojo.setShippingStreet(address.street());
        pojo.setShippingPostalCode(address.postalCode());
        pojo.setShippingPickupPointCode(address.pickupPointCode());
        order.getReservationId().ifPresent(id -> pojo.setReservationId(id.value()));
        order.getPaymentId().ifPresent(id -> pojo.setPaymentId(id.value()));
        order.getPaidAt().ifPresent(at -> pojo.setPaidAt(toOffsetDateTime(at)));
        order.getShippedAt().ifPresent(at -> pojo.setShippedAt(toOffsetDateTime(at)));
        order.getDeliveredAt().ifPresent(at -> pojo.setDeliveredAt(toOffsetDateTime(at)));
        order.getClosedAt().ifPresent(at -> pojo.setClosedAt(toOffsetDateTime(at)));
        pojo.setCreatedAt(toOffsetDateTime(order.getCreatedAt()));
        pojo.setUpdatedAt(toOffsetDateTime(order.getUpdatedAt()));
        return pojo;
    }

    public List<OrderItemsPojo> toOrderItemsPojos(Order order) {
        return order.getItems().stream().map(item -> {
            OrderItemsPojo pojo = new OrderItemsPojo();
            pojo.setId(item.getId().value());
            pojo.setOrderId(order.getId().value());
            pojo.setProductId(item.getProductId().value());
            pojo.setSellerId(item.getSellerId().value());
            pojo.setQuantity(item.getQuantity().value());
            pojo.setUnitPrice(item.getUnitPrice().amount());
            return pojo;
        }).toList();
    }

    private OrderItem toDomainItem(OrderItemsPojo pojo) {
        return new OrderItem(OrderItemId.of(pojo.getId()), ProductId.of(pojo.getProductId()),
            SellerId.of(pojo.getSellerId()), Quantity.of(pojo.getQuantity()), Money.of(pojo.getUnitPrice(), Money.RUB));
    }

    private Discount toDiscount(String kind, BigDecimal percentage, BigDecimal fixedAmount) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case DISCOUNT_PERCENTAGE -> new PercentageDiscount(percentage);
            case DISCOUNT_FIXED -> new FixedDiscount(Money.of(fixedAmount, Money.RUB));
            default -> throw new IllegalStateException("Unknown discount kind in DB: " + kind);
        };
    }

    private void applyDiscount(OrdersPojo pojo, Discount discount) {
        if (discount instanceof PercentageDiscount percentage) {
            pojo.setDiscountKind(DISCOUNT_PERCENTAGE);
            pojo.setDiscountPercentage(percentage.percentage());
        } else if (discount instanceof FixedDiscount fixed) {
            pojo.setDiscountKind(DISCOUNT_FIXED);
            pojo.setDiscountFixedAmount(fixed.amount().amount());
        }
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
