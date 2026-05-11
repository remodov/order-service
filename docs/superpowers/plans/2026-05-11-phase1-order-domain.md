# Ф1 — Домен `Order` (Tier C DDD) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **ВАЖНО (из CLAUDE.md):** шаги, ссылающиеся на `/ucp-*` скилл, исполняются **вызовом этого скилла через `Skill` tool в текущей сессии** — не пересказом его инструкций, не рукописным кодом, не `Agent`/`Task`-форком. Скиллы `/ucp-*` запускаются последовательно. Код доменного слоя генерирует `/ucp-ddd-tactical-design` по своему чек-листу и `.claude/docs/ddd-tactical-rules.md`; здесь — что должно получиться (acceptance criteria из спеки) и порядок, а не готовые сниппеты.

**Goal:** Реализовать чистый доменный слой (модуль `core`) bounded context `order` — агрегат `Order` с богатым ЖЦ, сущность `OrderItem`, value objects, enum статусов, доменные события, интерфейс репозитория и `core/service`-интерфейсы — строго по `docs/spec/03-order-service-model.md`, `04-order-service-lifecycle.md`, `06-order-service-rules.md`, `08-order-service-events.md`.

**Architecture:** Hexagonal `core` без Spring/jOOQ (compile-time изоляция через ArchUnit). Богатая доменная модель на библиотеке `ru.vikulinva:ddd-building-blocks` (`AggregateRoot`, `Entity`, `ValueObject`, `DomainEvent`, `AggregateRepository`). Инварианты — внутри агрегата; нарушение инварианта/перехода → доменное исключение. Без персистентности, REST, Kafka — это Ф2–Ф4.

**Tech Stack:** Java 21 (records, sealed, pattern matching), `ru.vikulinva:ddd-building-blocks:1.0.0`, `ru.vikulinva:usecase-pattern:1.1.0` (маркеры в `core`), Lombok (по `java-style-guide.md`), JUnit 5 + AssertJ для unit-тестов домена.

**Где мы:** Ф0 готова — пустой hexagonal-скелет, `core` содержит только package-info-заглушки в `domain/{aggregate,event,valueobject,repository}` и `port/out`, `usecase/{command,query}`. `./gradlew clean build` зелёный, ArchUnit hexagonal-тест PASS. Roadmap — `docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md`. `ddd-building-blocks` API: `ru.vikulinva.ddd.{AggregateRoot, Entity, ValueObject, DomainEvent, AggregateRepository, Specification, DomainEventPublisher, DomainEventHandler}` — резолвится из mavenLocal.

---

### Task 1: Pre-flight

**Files:** —

- [ ] **Step 1: Состояние ветки**

Run: `git status --short | grep -v '^??'` и `git log --oneline -3`
Expected: ветка `feat/order-service-from-scratch`, HEAD на коммите roadmap-апдейта Ф0 (`ac8b5d0` или новее), рабочее дерево чистое от изменений (untracked `CLAUDE.md`/`.claude` — норма).

- [ ] **Step 2: Перечитать секции спеки**

Прочитать (если ещё не в контексте): `docs/spec/03-order-service-model.md` (§3.1–3.3 — атрибуты `Order`, `OrderItem`, VO), `docs/spec/04-order-service-lifecycle.md` (статусы + матрица переходов + time-based), `docs/spec/06-order-service-rules.md` (`BR-001`..`BR-015`), `docs/spec/08-order-service-events.md` (каталог событий + структура `DomainEvent` + примеры `OrderConfirmed`/`OrderPaid`). И `.claude/docs/ddd-tactical-rules.md` (коды `R-AGG-*`, `R-VO-*`, `R-EVT-*`, `R-REPO-*`).

---

### Task 2: Доменный слой `Order` — `/ucp-ddd-tactical-design`

**Files (создаёт скилл, модуль `core`, package root `ru.vikulinva.orderservice`):**
- Create: `core/.../domain/valueobject/` — `Money.java` (BigDecimal amount + Currency, immutable, `add`/`subtract`/`multiply`, не допускает отрицательных), `Quantity.java` (1..999), `Discount.java` (sealed: `PercentageDiscount(BigDecimal pct)` / `FixedDiscount(Money amount)`), `Address.java` (страна/город/улица/индекс/ПВЗ-код), типизированные id `OrderId`/`OrderItemId`/`CustomerId`/`SellerId`/`ProductId`/`ReservationId`/`PaymentId` (обёртки над UUID, equals по значению), enum `OrderStatus` (`DRAFT`, `PENDING_PAYMENT`, `PAID`, `SHIPPED`, `DELIVERED`, `COMPLETED`, `EXPIRED`, `CANCELLED`, `REFUNDED`, `DISPUTE`), `CancellationReason.java` / `DisputeReason.java` если нужны.
- Create: `core/.../domain/entity/OrderItem.java` (Entity, уникальна в агрегате по `(productId, sellerId)`, `quantity`/`unitPrice`, `lineTotal()` вычисляемое).
- Create: `core/.../domain/aggregate/Order.java` (AggregateRoot) с методами: `addItem(productId, sellerId, qty, unitPrice)`, `removeItem(...)`, `applyPromo(Discount)`, `removePromo()`, `confirm()`, `fixReservation(ReservationId)`, `pay(PaymentId, Instant)`, `markShipped(shipmentRef)`, `confirmDelivery(Instant)`, `complete(Instant)`, `expire()`, `cancel()`, `openDispute(reason)`, `resolveDispute(decision)`. `total()` пересчитывается при изменении позиций/промо (`BR-001`). Переходы статусов строго по матрице §4; нарушение → доменное исключение. Инварианты `BR-001` (согласованность суммы), `BR-003` (один промокод — `applyPromo` при наличии скидки → исключение), `BR-004` (после `PENDING_PAYMENT` `unitPrice` не меняется — `addItem`/`removeItem`/`applyPromo` только в `DRAFT`), `BR-012` (скидка ≤ суммы — обрезается, без минуса), `BR-013` (`confirm()` требует `total >= 100 RUB`), `BR-014` (один заказ — один продавец: `addItem` с другим `sellerId` → исключение). Регистрация доменных событий через `registerEvent(...)`.
- Create: `core/.../domain/event/` — `OrderCreated`, `OrderConfirmed`, `OrderReservationFailed`, `OrderPaid`, `OrderShipped`, `OrderDelivered`, `OrderCompleted`, `OrderCancelled`, `OrderExpired`, `DisputeOpened`, `DisputeResolved`, `OrderRefunded`, `OrderPaymentFailed` — каждое `final class extends DomainEvent`, базовые поля (`id` UUID, `occurredAt` Instant, `aggregateType "Order"`, `aggregateId` orderId) из `DomainEvent`, плюс типобезопасный payload по §8 (для `OrderConfirmed` — items snapshot, total, customerId, sellerId; для `OrderPaid` — paymentId, amount, paidAt; и т.д. — точные поля по карточкам в `docs/spec/14-order-service-integrations/`).
- Create: `core/.../domain/repository/OrderRepository.java` (extends `AggregateRepository<Order, OrderId>` или аналог): `findById(OrderId)`, `findByIdForUpdate(OrderId)` (для FOR UPDATE-загрузки в command-handler'ах, `R-REPO-*`), `save(Order)`, `findByIdempotencyKey(...)` — нет, idempotency_keys отдельный port (Ф3). Минимум: `findById`, `findByIdForUpdate`, `save`.
- Create: `core/.../service/DateTimeService.java` (`Instant now()`), `core/.../service/UuidGenerator.java` (`UUID generate()`) — системные источники недетерминизма за интерфейсом (`BS-5`).
- Create: `core/.../domain/exception/` — иерархия доменных исключений (`OrderDomainException` базовый + `OrderInvalidStateException`, `OrderBelowMinimumException`, `EmptyOrderException`, `MultiSellerNotSupportedException`, `PromoAlreadyAppliedException`, `OrderNotFoundException`, `DisputeWindowClosedException`, …) — коды соответствуют `13-order-service-errors.md` (HTTP-маппинг — в Ф3 `/ucp-error-handling-design`, не здесь).
- Create: package-info.java дополняются/заменяются на реальные (заглушки Ф0 уже есть).
- Create (опц., если скилл генерирует): `core/src/test/java/.../domain/aggregate/OrderTest.java` и тесты VO — unit-тесты на инварианты и переходы.

- [ ] **Step 1: Вызвать скилл**

Вызвать `Skill` tool с `skill=ucp-ddd-tactical-design`. Контекст: «Order Service, bounded context `order`, package root `ru.vikulinva.orderservice`, модуль `core` (без Spring/jOOQ), библиотека `ru.vikulinva:ddd-building-blocks` (`ru.vikulinva.ddd.{AggregateRoot,Entity,ValueObject,DomainEvent,AggregateRepository}`). Сгенерировать целиком доменную модель агрегата `Order` по `docs/spec/03-order-service-model.md` (§3.1–3.3), `04-order-service-lifecycle.md` (статусы + матрица переходов §4), `06-order-service-rules.md` (инварианты `BR-001`,`BR-003`,`BR-004`,`BR-012`,`BR-013`,`BR-014` — внутри агрегата), `08-order-service-events.md` (13 доменных событий + структура). Список артефактов — см. секцию Files этой задачи. Дополнительно — `core/service/DateTimeService` (`Instant now()`) и `core/service/UuidGenerator` (`UUID generate()`). Иерархия доменных исключений с кодами из `13-order-service-errors.md` (HTTP-маппинг НЕ здесь). Не трогать persistence/REST/Kafka. Lombok-defaults по `java-style-guide.md`. Точные поля payload событий — сверять с карточками в `docs/spec/14-order-service-integrations/`.» Следовать чек-листу скилла.

- [ ] **Step 2: Сборка `core`**

Run: `./gradlew :core:build --console=plain`
Expected: BUILD SUCCESSFUL — `core` компилируется (без Spring/jOOQ-импортов), unit-тесты домена (если скилл их сгенерировал) зелёные.

- [ ] **Step 3: Проверить покрытие unit-тестами (acceptance criteria)**

Убедиться, что есть и зелёные тесты на:
- VO: `Money` (отрицательная сумма → исключение; `add`/`subtract`/`multiply` immutable), `Quantity` (0 и >999 → исключение), `Discount` (sealed-ветки), типизированные id (equals по значению).
- `Order` — инварианты: `BR-001` (`total` = sum(lineTotal) − discount + shippingFee пересчитывается), `BR-012` (скидка больше суммы → total = 0, не минус), `BR-013` (`confirm()` при `total < 100` → `OrderBelowMinimumException`), `BR-014` (`addItem` с другим sellerId → `MultiSellerNotSupportedException`), `BR-003` (`applyPromo` при уже применённом → `PromoAlreadyAppliedException`), `BR-004` (`addItem`/`applyPromo` в `PENDING_PAYMENT` → `OrderInvalidStateException`).
- `Order` — переходы (по матрице §4): `DRAFT → confirm() → PENDING_PAYMENT` (+ событие `OrderConfirmed`, требует ≥1 позиции иначе `EmptyOrderException`); `PENDING_PAYMENT → pay() → PAID` (+ `OrderPaid`); `PENDING_PAYMENT → expire() → EXPIRED`; `PAID → markShipped() → SHIPPED`; `SHIPPED → confirmDelivery() → DELIVERED`; `DELIVERED → complete() → COMPLETED`; `DELIVERED → openDispute() → DISPUTE`; `DISPUTE → resolveDispute(BUYER) → REFUNDED` / `resolveDispute(SELLER) → COMPLETED`; `PAID/SHIPPED → cancel() → CANCELLED`; `DRAFT/PENDING_PAYMENT → cancel() → CANCELLED`. Невалидный переход (напр. `markShipped()` в `DRAFT`) → `OrderInvalidStateException`. Терминальные (`COMPLETED`/`EXPIRED`/`REFUNDED`) — любой переход → исключение.

Если каких-то тестов нет — добить их в `core/src/test/java/.../domain/` (plain JUnit 5 + AssertJ, поверх API, сгенерированного на Step 1; `@MockitoBean` тут не нужен — домен чистый). Запустить `./gradlew :core:test` — зелёный.

- [ ] **Step 4: Commit**

```bash
git add core
git commit -m "feat(Ф1): доменный слой Order — агрегат, OrderItem, VO, события, репозиторий, core/service

Сгенерировано /ucp-ddd-tactical-design по docs/spec/03/04/06/08. Инварианты BR-001/003/004/012/013/014
внутри агрегата; переходы статусов по матрице §4; 13 доменных событий; OrderRepository (findByIdForUpdate);
DateTimeService/UuidGenerator. Unit-тесты на инварианты и переходы — зелёные.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Ревью домена — `/ucp-ddd-tactical-review`

**Files:** — (правки по findings — через повторный `/ucp-ddd-tactical-design`)

- [ ] **Step 1: Вызвать скилл**

Вызвать `Skill` tool с `skill=ucp-ddd-tactical-review`. Контекст: «Ревью изменённого доменного кода Ф1 — `git diff <предыдущий-Ф0-коммит>..HEAD -- core/`. Проверить на соответствие `ddd-tactical-rules.md` (`R-AGG-*`, `R-VO-*`, `R-EVT-*`, `R-REPO-*`): rich domain (не anemic), инварианты в агрегате, VO immutable + equals по значению, события `final extends DomainEvent` без бизнес-логики, репозиторий — интерфейс в `core`, нет Spring/jOOQ-импортов в `core`.»

- [ ] **Step 2: Исправить findings**

Findings уровня MUST — исправить **через повторный вызов `/ucp-ddd-tactical-design`** на затронутых артефактах (не вручную). Findings уровня SHOULD — на усмотрение. После правок — `./gradlew :core:build` зелёный.

- [ ] **Step 3: Commit (если были правки)**

```bash
git add core
git commit -m "fix(Ф1): правки домена по /ucp-ddd-tactical-review

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Production-бины `DateTimeService` / `UuidGenerator` в bootstrap

**Files:**
- Modify: `bootstrap/src/main/java/ru/vikulinva/orderservice/config/ServiceBeansConfig.java`

- [ ] **Step 1: Добавить два бина под `@ConditionalOnMissingBean`**

В `ServiceBeansConfig` рядом с существующим `systemClock()` добавить:

```java
@Bean
@ConditionalOnMissingBean
public ru.vikulinva.orderservice.service.DateTimeService dateTimeService(java.time.Clock clock) {
    return () -> java.time.Instant.now(clock);
}

@Bean
@ConditionalOnMissingBean
public ru.vikulinva.orderservice.service.UuidGenerator uuidGenerator() {
    return java.util.UUID::randomUUID;
}
```

(Импорты привести в порядок по `java-style-guide.md` — не оставлять FQN в теле, вынести в `import`. Точные сигнатуры интерфейсов взять из того, что сгенерировал Task 2 — если `DateTimeService` имеет метод не `now()`, а другой — привести лямбды в соответствие.)

- [ ] **Step 2: Сборка**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, ArchUnit hexagonal-тест PASS.

- [ ] **Step 3: Smoke `bootRun` на `local`**

Run: `docker compose up -d postgres` (дождаться healthy), затем `./gradlew :bootstrap:bootRun --args='--spring.profiles.active=local --server.port=18080'`, дождаться `Started OrderServiceApplication`, `curl -s http://localhost:18080/actuator/health` → `{"status":"UP"}`, остановить (`pkill -f bootRun`), `docker compose down`.
Expected: контекст поднимается; `DateTimeService`/`UuidGenerator`-бины не ломают старт (handler'ов, их инжектящих, ещё нет — но бины должны резолвиться).

- [ ] **Step 4: Commit**

```bash
git add bootstrap
git commit -m "feat(Ф1): production-бины DateTimeService / UuidGenerator в ServiceBeansConfig

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Финальная проверка фазы + roadmap

**Files:**
- Modify: `docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md`

- [ ] **Step 1: `./gradlew clean build`**

Expected: BUILD SUCCESSFUL, ArchUnit PASS, все unit-тесты домена зелёные.

- [ ] **Step 2: Отметить Ф1 в roadmap**

В разделе «Статус» roadmap-doc: «Ф1 — ГОТОВА» + список коммитов; в заголовке «### Ф1 — Домен `Order` (Tier C DDD)» добавить ✅.

- [ ] **Step 3: Commit roadmap**

```bash
git add docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md
git commit -m "docs: roadmap — Ф1 готова

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Чекпоинт с пользователем**

Доложить итог Ф1, спросить — переходим к Ф2 (Persistence + Outbox: `/ucp-pg-schema-design` → `/ucp-pg-schema-review` → `/ucp-jooq-design` → `/ucp-pg-runtime-design`) через новый `superpowers:writing-plans`, или пауза.

---

## Self-review

- **Покрытие Ф1 из roadmap:** Task 2 = `/ucp-ddd-tactical-design` (агрегат `Order` + методы ЖЦ ✓, `OrderItem` ✓, VO `Money`/`Quantity`/`Discount`/`Address`/типизированные id ✓, enum `OrderStatus` ✓, 13 доменных событий ✓, `OrderRepository` с `findByIdForUpdate` ✓, инварианты `BR-001/003/004/012/013/014` ✓, `core/service` интерфейсы ✓); Task 3 = `/ucp-ddd-tactical-review` ✓; Task 4 = production-бины `DateTimeService`/`UuidGenerator` (roadmap Ф1 явно их упоминает) ✓; Task 5 = выход фазы ✓. Доменные исключения с кодами `13-errors` — Task 2 (HTTP-маппинг отложен в Ф3, как в roadmap). Пробелов нет.
- **Плейсхолдеры:** код домена генерирует `/ucp-ddd-tactical-design` по чек-листу — это намеренное делегирование (CLAUDE.md), а не «TODO/fill later»; конкретика для исполнителя — в acceptance criteria Step 3 Task 2 (список инвариантов BR-* и переходов §4, которые ОБЯЗАНЫ быть покрыты тестами) и в детальном промпте Step 1 Task 2. Task 4 содержит готовый код двух бинов.
- **Согласованность:** package root `ru.vikulinva.orderservice`, модуль `core`, имена VO/событий/статусов — как в спеке §3/§4/§8 и одинаковы между задачами; `DateTimeService.now()`/`UuidGenerator.generate()` — Task 2 определяет, Task 4 использует (с оговоркой «привести лямбды к фактическим сигнатурам»).
- **Риск:** `OrderRepository` extends `AggregateRepository<Order, OrderId>` — точная сигнатура базового интерфейса из `ddd-building-blocks` (есть ли там `findByIdForUpdate` или его надо добавлять в `OrderRepository`) определится при вызове скилла; не блокирует.
