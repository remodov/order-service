# Order Service — roadmap реализации с нуля (Tier C / UCP Level 3)

> Дата: 2026-05-11. Это **roadmap фазировки**, не дизайн-спека: дизайн уже есть и
> является источником правды — `docs/spec/order-service.md` + 17 разделов. Здесь —
> только порядок реализации и привязка фаз к `/ucp-*` скиллам.

## Контекст

- Рабочая директория зачищена; в git HEAD (`b44e584`) лежит предыдущая реализация,
  её **игнорируем** — строим заново строго по `docs/spec/`.
- Стек (по `17-order-service-stack.md`): Java 21, Spring Boot 3.4.x, Gradle Kotlin DSL
  multi-module hexagonal, jOOQ 3.19 + **Liquibase** (миграции — решено с пользователем
  2026-05-11: строка «Flyway» в `17-stack.md` — неточность спеки; вся UCP-обвязка и
  репо-конвенция на Liquibase), PostgreSQL 16+, Kafka,
  `ru.vikulinva:usecase-pattern(-starter)`, `ru.vikulinva:ddd-building-blocks`,
  `ru.vikulinva:hexagonal-architecture-{core,test}` (артефакты в mavenLocal / GitHub
  Packages `remodov/...`), MapStruct, Resilience4j, Spring Security OAuth2 Resource
  Server, Redis (кэш), Micrometer/OTel, Testcontainers + WireMock.
- Модули (репо-конвенция, не «persistence»): `core`, `adapter-in-rest`, `adapter-in-kafka`,
  `adapter-out-postgres`, `adapter-out-payment`, `adapter-out-catalog`, `adapter-out-kafka`,
  `bootstrap`, `test-utils`. (Один `adapter-in-rest` на customer/seller/admin — осознанное
  отступление от `R-HEX-MOD-X3` в пользу репо-конвенции; ABAC/RBAC внутри сервиса.)
- Подход: **вертикальные срезы, MVP-first** (см. CLAUDE.md, блок «Структура плана»).
  Ф0 — единственная горизонтальная (инфраструктурный bootstrap). Дальше каждая
  фаза = работающий end-to-end кусок: собирается, запускается, покрыт тестами.
- Каждая фаза = отдельный цикл spec → `superpowers:writing-plans` → `executing-plans`.
  Сейчас детализируем планом только Ф0; следующие фазы планируем по мере готовности.

## Объём (для контекста, из спеки)

- Агрегат `Order`, 10 статусов (`DRAFT`→`PENDING_PAYMENT`→`PAID`→`SHIPPED`→`DELIVERED`→`COMPLETED`; ветки `EXPIRED`/`CANCELLED`/`DISPUTE`/`REFUNDED`).
- ~13 команд/UC: `CreateOrder`, `AddItem`/`RemoveItem`, `ApplyPromo`/`RemovePromo`, `ConfirmOrder`, `MarkShipped`, `ConfirmDelivery`, `CancelOrder`, `OpenDispute`, `ResolveDispute`(Buyer/Seller) + event-handlers (`HandleItemReserved`, `HandleReservationFailed`, `HandlePaymentSucceeded`, `HandlePaymentFailed`, `HandleReservationReleased`, `HandleRefundIssued`, `HandleRefundFailed`).
- ~13 доменных событий (Outbox → Kafka `marketplace.orders.v1`) + 2 saga-команды (`ReleaseReservationRequested`, `RefundPaymentRequested` → `marketplace.orders.saga.v1`).
- 2 saga: Confirm Order (оркестрация через статус агрегата + `reservationId`); Process Refund (таблица `refund_sagas`, статусы `STARTED`/`INVENTORY_RELEASED`/`PAYMENT_REFUNDING`/`COMPLETED`/`FAILED`). + scheduled jobs `ExpireUnpaidOrdersJob` (15 мин), `CloseDeliveredOrdersJob` (14 дней).
- 4 интеграции: Catalog (REST sync — цены/промокоды), Payment (REST out + Kafka in), Inventory (Kafka in/out), Notification (Kafka out).
- Read Model: `order_summaries`, `order_timelines` (проекции через `@TransactionalEventListener(AFTER_COMMIT)`); кэш `seller-orders` (Redis TTL 30s).
- Инфра-таблицы: `outbox`, `processed_events`, `idempotency_keys`, `refund_sagas`, `order_audit_log`.
- Auth: RBAC (`customer`/`seller`/`admin`/`system`) + ABAC по владению; audit log admin-команд; шифрование `Address` at-rest.
- 15 бизнес-правил `BR-001..BR-015`; каталог ошибок RFC 9457 (`13-order-service-errors.md`).

## Статус

- **Ф0 — ГОТОВА** (ветка `feat/order-service-from-scratch`, коммиты `1e5f7a2` skeleton, `a56ad30` bootstrap). `./gradlew clean build` зелёный, ArchUnit hexagonal-тест PASS, `bootRun --spring.profiles.active=local` поднимается (`docker compose up -d postgres` нужен), `/actuator/health` = UP, без обращений к Keycloak/Kafka. Прежняя реализация (119 .java) удалена; модули пустые (package-info-заглушки), `order-service.openapi.yaml` — placeholder `paths: {}`, `changelog-master.yaml` — пустой baseline.
- **Ф1 — ГОТОВА** (план `docs/superpowers/plans/2026-05-11-phase1-order-domain.md`; коммиты `feat(Ф1): доменный слой Order …`, `feat(Ф1): production-бины DateTimeService / UuidGenerator …`). Модуль `core`: VO (`Money`/`Quantity`/`Discount`+`PercentageDiscount`/`FixedDiscount`/`Address`/типизированные id/`OrderStatus`/`DisputeDecision`/`DisputeReason`), Entity `OrderItem` (immutable), AggregateRoot `Order` (методы ЖЦ по матрице §4, инварианты `BR-001/003/004/012/013/014`, `create()`/`fromPersistence()`, время — параметром `Instant now`), 13 доменных событий + `OrderItemSnapshot`, `OrderRepository extends AggregateRepository + findByIdForUpdate`, `core/service` `DateTimeService`/`UuidGenerator`, иерархия доменных исключений с кодами из `13-errors`. Lombok добавлен модульно (`subprojects`). 45 unit-тестов (инварианты + матрица переходов + споры BR-007) — зелёные. Production-бины `DateTimeService`/`UuidGenerator` в `ServiceBeansConfig`. `./gradlew clean build` зелёный, ArchUnit PASS, `bootRun` на `local` UP. Ревью `/ucp-ddd-tactical-review` — только Замечания (слабая типизация `OrderInvalidStateException.expected`, `[serial]` без `serialVersionUID`, конфликт `R-MOD-1` ↔ `BS-5` по `core/service/`, `@Getter` на событиях), без MUST. **Долги Ф1:** HTTP-маппинг доменных исключений → RFC 9457 — в Ф3 (`/ucp-error-handling-design`); `OrderConfirmed` etc. сериализуются с `createdAt` (от `DomainEvent` либ) — спека-контракт говорит `occurredAt`, сверить в Ф4.
- Ф2–Ф8 — не начаты.

## Фазы

### Ф0 — Skeleton & bootstrap (горизонтальная, инфра) ✅
- `/ucp-hexagonal-design` — multi-module gradle skeleton: `core`, `persistence`, `adapter-in-rest`, `adapter-in-kafka`, `adapter-out-catalog`, `adapter-out-payment`, `adapter-out-kafka` (publisher), `bootstrap`. `settings.gradle.kts` + per-module `build.gradle.kts` (core без Spring/jOOQ), `Application.java` в bootstrap, `package-info.java` в `core/order/{aggregate,port}`, ArchUnit base test.
- `/ucp-bootstrap-design` — профили `local` / `integration-test` / `production`; production-бины `Clock` / UUID-провайдера; `SecurityConfig` per profile; **Flyway** конфиг; jOOQ codegen из живой схемы; гейтинг Kafka-листенеров по профилю; Jackson-видимость event-payload; `application.yml` каркас.
- Выход фазы: `./gradlew build` зелёный, `bootRun` поднимается на `local` без живых Keycloak/Kafka.

### Ф1 — Домен `Order` (Tier C DDD) ✅
- `/ucp-ddd-tactical-design` — агрегат `Order` (методы `addItem`/`removeItem`/`applyPromo`/`removePromo`/`confirm`/`fixReservation`/`pay`/`ship`/`confirmDelivery`/`close`/`expire`/`cancel`/`openDispute`/`resolveDispute`), сущность `OrderItem`, VO (`Money`, `Quantity`, `Discount` sealed: `PercentageDiscount`/`FixedDiscount`, `Address`, типизированные id `OrderId`/`OrderItemId`/`CustomerId`/`SellerId`/`ProductId`/`ReservationId`/`PaymentId`), enum `OrderStatus`, все доменные события (`OrderCreated`, `OrderConfirmed`, `OrderReservationFailed`, `OrderPaid`, `OrderShipped`, `OrderDelivered`, `OrderCompleted`, `OrderCancelled`, `OrderExpired`, `DisputeOpened`, `DisputeResolved`, `OrderRefunded`, `OrderPaymentFailed`), интерфейс `OrderRepository` (с `findByIdForUpdate`). Инварианты `BR-001`, `BR-003`, `BR-004`, `BR-012`, `BR-013`, `BR-014` — внутри агрегата.
- → `/ucp-ddd-tactical-review`.
- Выход: домен компилируется, unit-тесты на инварианты и переходы зелёные.

### Ф2 — Persistence + Outbox-relay
- `/ucp-pg-schema-design` → `/ucp-pg-schema-review` — Flyway changeset'ы: `orders`, `order_items`, `outbox`, `processed_events`, `idempotency_keys`, `refund_sagas`, `order_summaries`, `order_timelines`, `order_audit_log`; индексы из `03-model` и `09-queries`; enum `order_status`; шифрование `Address` (pgcrypto / app-level — решит skill).
- `/ucp-jooq-design` — `JooqOrderRepository` (DSLContext + multiset для `order_items`), `OrderDomainRecordMapper`, `OrderFilterConditionBuilder`.
- `/ucp-pg-runtime-design` — outbox-relay `@Scheduled` job (`SELECT … FOR UPDATE SKIP LOCKED` по `outbox WHERE published_at IS NULL`), `lock_timeout`.
- Выход: интеграционный тест save→load `Order` через Testcontainers PG зелёный; outbox-строка пишется в той же транзакции.

### Ф3 — Happy-path команды (UC-1, синхронная часть)
- `/ucp-api-design` — OpenAPI `order-service.openapi.yaml` (эндпоинты `POST /api/v1/orders`, `POST /orders/{id}/items` и т.д., `POST /orders/{id}/confirm`), генерация DTO + контроллера, `useBeanValidation`.
- `/ucp-pattern-design` (по одной команде) — `CreateOrderUseCase` (+ idempotency через `idempotency_keys`, `BR-010`), `AddItemUseCase`/`RemoveItemUseCase`, `ApplyPromoUseCase`/`RemovePromoUseCase`, `ConfirmOrderUseCase` (FOR UPDATE, `BR-002`/`BR-013`, регистрация `OrderConfirmed`).
- `/ucp-integration-design` — `adapter-out-catalog`: port `CatalogPort` в core, client-generator (openapi-generator `spring-restclient`), `CatalogClientAdapter` (Resilience4j `@CircuitBreaker`/`@Retry`/`@Bulkhead`), mapper, HealthIndicator, exception hierarchy (`14-…-to-catalog.md`).
- `/ucp-error-handling-design` — иерархия `DomainException`/`ValidationException`/`IntegrationException`/`TechnicalException`, `GlobalExceptionHandler` (@RestControllerAdvice → RFC 9457 ProblemDetails), маппинг кодов из `13-errors`, метрика `app_errors_total`.
- → `/ucp-pattern-review` + `/ucp-api-review`; тесты — `/ucp-test-design`.
- Выход: создать→добавить позиции→применить промо→подтвердить заказ работает E2E через REST + WireMock Catalog; integration-тесты по релевантным AC зелёные.

### Ф4 — Confirm Order saga (UC-1, асинхронная часть)
- `/ucp-kafka-design` — `KafkaConfig` (idempotent producer + JsonSerializer + manual-ack consumer), `KafkaSettings` (@ConfigurationProperties + @Validated), `adapter-out-kafka` publisher из outbox-relay; `adapter-in-kafka` consumers.
- `/ucp-distributed-design` — idempotent consumer на `processed_events` (`BR-011`); inbox при необходимости.
- `/ucp-pattern-design` — `HandleItemReserved`, `HandleReservationFailed` (`→ DRAFT` + `OrderReservationFailed`), `HandlePaymentSucceeded` (`→ PAID` + `OrderPaid`), `HandlePaymentFailed` (`→ DRAFT` + `OrderPaymentFailed`), `MarkShippedUseCase` (`BR-005`), `ConfirmDeliveryUseCase`; `ExpireUnpaidOrdersJob` (`@Scheduled`, SKIP LOCKED).
- `/ucp-integration-design` — Payment: port `PaymentPort` (REST out — запуск платежа из BFF? фактически Order шлёт `POST /payments` — уточнить по `14-…-to-payment.md`); Inventory — только Kafka in/out (контракты `14-…-from-inventory-itemreserved.md`).
- Интеграции Notification — подписчик на наши события, со стороны Order ничего не пишем сверх публикации в `marketplace.orders.v1`.
- → review-пары; `/ucp-test-design` (Testcontainers Kafka — но базовый класс по командному Test Strategy без Kafka; для saga — отдельный набор с эмбеддед Kafka либо ручная подача в `@KafkaListener`).
- Выход: confirm → (Kafka ItemReserved/PaymentSucceeded) → `PAID` → `MarkShipped` → `ConfirmDelivery` проходит; таймаут оплаты → `EXPIRED`.

### Ф5 — Read Model & queries (UC-5, UC-6, чтение для UC-7)
- `/ucp-cqrs-design` — `GetOrderByIdQuery` (write-side, RYW), `ListMyOrdersQuery` (`order_summaries`, ABAC `customerId == jwt.sub`), `ListSellerOrdersQuery` (`primary_seller_id`), `GetOrderTimelineQuery` (`order_timelines`); read-DTO records; проекции `@TransactionalEventListener(AFTER_COMMIT)` на `OrderCreated`/`OrderPaid`/`OrderShipped`/`OrderDelivered`/`OrderCompleted`/`OrderCancelled`/`OrderRefunded`; `Jooq*ViewRepository`.
- `/ucp-caching-design` — `@Cacheable("seller-orders")` Redis TTL 30s; JWK Set cache (5 мин — фактически в `/ucp-auth-design`).
- → `/ucp-cqrs-review`; `/ucp-test-design`.
- Выход: списки заказов покупателя/продавца + timeline работают; eventual consistency лаг < 1s в тестах.

### Ф6 — Refund saga & disputes (UC-2, UC-3, UC-7)
- `/ucp-distributed-design` — saga `ProcessRefund`: таблица `refund_sagas`, оркестратор, шаги `ReleaseReservationRequested` → `ReservationReleased` → `RefundPaymentRequested` → `RefundIssued`/`RefundFailed`; compensation; retry от outbox-relay (3 попытки) → алёрт оператору на `FAILED`; `BR-009` (минус-баланс продавца).
- `/ucp-pattern-design` — `CancelOrderUseCase` (`BR-006`: `DRAFT`/`PENDING_PAYMENT` → `CANCELLED` без возврата; `PAID`/`SHIPPED` → запуск `ProcessRefund`), `OpenDisputeUseCase` (`BR-007`: окно 14 дней, `→ DISPUTE`), `ResolveDisputeUseCase` (`decision: BUYER → REFUNDED + ProcessRefund` | `SELLER → COMPLETED`); `HandleReservationReleased`, `HandleRefundIssued`, `HandleRefundFailed`; `CloseDeliveredOrdersJob` (`@Scheduled` ежедневно, `→ COMPLETED` + `OrderCompleted`).
- `/ucp-integration-design` — Payment refund-команда; Inventory release-команда (saga-топик `marketplace.orders.saga.v1`).
- → review-пары; `/ucp-test-design`.
- Выход: отмена оплаченного заказа → `REFUNDED`; спор → решение оператора → `REFUNDED`/`COMPLETED`; close-delivered job переводит `DELIVERED → COMPLETED` после 14 дней.

### Ф7 — Auth, observability, security hardening
- `/ucp-auth-design` — Spring Security OAuth2 RS, валидация JWT + JWK cache (5 мин), RBAC на эндпоинтах (матрица §5), ABAC-хелперы (`AuthenticatedCustomer`, `AuthenticatedSeller`, `@Component("access")`), audit-log аспект (`order_audit_log`, admin-команды), идемпотентность money-команд (cross-ref AUTH-19), шифрование `Address`.
- `/ucp-observability-design` — Logback JSON для prod, Micrometer + Prometheus с тегами `service/env/version`, OTel автоинструментация + sampling, Actuator liveness/readiness + custom HealthIndicator'ы, `MdcFilter` (`orderId`/`requestId`/`userId`), custom-метрики (`outbox_lag_seconds`, `processed_events_total{kind=duplicate}`, `orders_status_count`).
- `/ucp-security-design` — SAST в CI (Error Prone, SpotBugs+FindSecBugs, OWASP Dependency-Check, Gitleaks, Trivy), Dockerfile non-root + digest-pinned, suppressions с обоснованием.
- `/ucp-shutdown-design` (если есть) — graceful shutdown, drain Kafka-листенеров, k8s preStop.
- → `/ucp-auth-review`, `/ucp-security-review`, `/ucp-observability-review`.
- Выход: эндпоинты закрыты по ролям и ABAC; admin-команды в audit log; CI красит на новых уязвимостях.

### Ф8 — Тестовое покрытие по `15-acceptance.md`
- `/ucp-test-design` — пройтись по всем AC, добить недостающие integration-тесты (Testcontainers PG + WireMock, детерминированное время/UUID через `@MockitoBean`, `DatabasePreparer` + `TestObjectGenerator`), E2E REST-сценарии UC-1..UC-7.
- Выход: все AC из `15-…-acceptance.md` покрыты, `./gradlew check` зелёный.

## Открытые вопросы (решаются в соответствующих фазах, не блокируют Ф0)

1. **Кто вызывает Payment `POST /payments`** — BFF (как в `12-sagas` шаг par) или Order Service? По `01-context` и `17-stack` — Order делает sync REST в Payment. Уточнить по `14-order-service-integrations/order-service-to-payment.md` в Ф4.
2. **Outbox-relay**: своя `@Scheduled` job (берём в Ф2, по спеке «в первой версии») vs Debezium — берём свою.
3. **Шифрование `Address`**: pgcrypto на уровне БД vs app-level (Spring `@Convert`/jOOQ converter) — решит `/ucp-pg-schema-design` / `/ucp-bootstrap-design` в Ф2.
4. **Kafka в integration-тестах**: командный Test Strategy запрещает Kafka в базовом классе — saga-тесты Ф4/Ф6 либо отдельным набором с embedded Kafka, либо подачей событий напрямую в `@KafkaListener`-метод. Решит `/ucp-test-design`.
5. **`order_status` enum в PG** vs `varchar` + CHECK — решит `/ucp-pg-schema-design`.

## Следующий шаг

`superpowers:writing-plans` → детальный план **только Ф0** (Skeleton & bootstrap),
шаги — вызовы `/ucp-hexagonal-design` и `/ucp-bootstrap-design` с проверками сборки.
