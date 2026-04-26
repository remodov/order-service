package ru.vikulinva.orderservice.adapter.in.rest.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.vikulinva.hexagonal.InboundAdapter;
import ru.vikulinva.orderservice.adapter.in.rest.generated.api.OrdersApi;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.CancelOrderRequest;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.CreateOrderRequest;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.Order;
import ru.vikulinva.orderservice.adapter.in.rest.mapper.OrderRestMapper;
import ru.vikulinva.orderservice.adapter.in.rest.mapper.RequestHashCalculator;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.vikulinva.orderservice.adapter.in.rest.security.AuthenticatedCustomer;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.usecase.command.CancelOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.ConfirmOrderUseCase;
import ru.vikulinva.orderservice.usecase.command.CreateOrderUseCase;
import ru.vikulinva.orderservice.usecase.query.GetOrderByIdQuery;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.net.URI;
import java.util.UUID;

/**
 * REST-контроллер для заказов. Реализует сгенерированный из OpenAPI интерфейс
 * {@link OrdersApi}. Контроллер делает только одно: маппит запрос →
 * {@link CreateOrderUseCase}, диспатчит, маппит обратно в JsonBean.
 */
@RestController
@InboundAdapter("REST controller for orders (UC-1, UC-5)")
public class OrderController implements OrdersApi {

    private final UseCaseDispatcher useCaseDispatcher;
    private final OrderRestMapper mapper;
    private final RequestHashCalculator requestHashCalculator;
    private final AuthenticatedCustomer authenticatedCustomer;

    public OrderController(UseCaseDispatcher useCaseDispatcher,
                            OrderRestMapper mapper,
                            RequestHashCalculator requestHashCalculator,
                            AuthenticatedCustomer authenticatedCustomer) {
        this.useCaseDispatcher = useCaseDispatcher;
        this.mapper = mapper;
        this.requestHashCalculator = requestHashCalculator;
        this.authenticatedCustomer = authenticatedCustomer;
    }

    @Override
    @PreAuthorize("hasRole('customer') or hasRole('admin')")
    public ResponseEntity<Order> createOrder(UUID idempotencyKey, CreateOrderRequest request) {
        var customerId = authenticatedCustomer.currentCustomerId();
        var requestHash = requestHashCalculator.hash(request);

        CreateOrderUseCase useCase = mapper.toUseCase(customerId, request, idempotencyKey.toString(), requestHash);
        var order = useCaseDispatcher.dispatch(useCase);
        Order body = mapper.toRest(order);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/orders/" + body.getId())
            .body(body);
    }

    @Override
    @PreAuthorize("hasRole('customer') or hasRole('admin')")
    public ResponseEntity<Order> confirmOrder(UUID id) {
        var customerId = authenticatedCustomer.currentCustomerId();
        var useCase = new ConfirmOrderUseCase(OrderId.of(id), customerId);
        var order = useCaseDispatcher.dispatch(useCase);
        return ResponseEntity.ok(mapper.toRest(order));
    }

    @Override
    @PreAuthorize("hasRole('customer') or hasRole('admin')")
    public ResponseEntity<Order> cancelOrder(UUID id, CancelOrderRequest request) {
        var customerId = authenticatedCustomer.currentCustomerId();
        var reason = new CancellationReason(request.getReasonCode(), request.getComment());
        var useCase = new CancelOrderUseCase(OrderId.of(id), customerId, reason);
        var order = useCaseDispatcher.dispatch(useCase);
        return ResponseEntity.ok(mapper.toRest(order));
    }

    @Override
    @PreAuthorize("hasRole('customer') or hasRole('admin')")
    public ResponseEntity<Order> getOrderById(java.util.UUID id) {
        var customerId = authenticatedCustomer.currentCustomerId();
        var order = useCaseDispatcher.dispatch(new GetOrderByIdQuery(OrderId.of(id), customerId));
        return ResponseEntity.ok(mapper.toRest(order));
    }
}
