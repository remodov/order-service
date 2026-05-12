# Ф2 — Persistence + Outbox-relay — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (inline — `/ucp-*` скиллы запускаются через `Skill` tool в текущей сессии, не форком). Steps use checkbox (`- [ ]`) syntax.
>
> **ВАЖНО (CLAUDE.md):** шаги со ссылкой на `/ucp-*` — вызов скилла через `Skill`, не пересказ. DDL и миграции — **обязательный** review (`/ucp-pg-schema-review`). Скиллы запускаются последовательно.

**Goal:** Реализовать слой персистентности (модуль `adapter-out-postgres`) — Liquibase-схему всех таблиц, jOOQ codegen из применённой схемы, `JooqOrderRepository` (реализация `OrderRepository` из `core`, atomic save аггрегата + запись доменных событий в `outbox` в той же транзакции, `findByIdForUpdate`), маппер jOOQ-POJO ↔ доменная модель, и Outbox-relay (`@Scheduled`-job, `SELECT … FOR UPDATE SKIP LOCKED`).

**Architecture:** Hexagonal — `adapter-out-postgres` зависит от `core`, реализует port `OrderRepository`. Только jOOQ (без JdbcTemplate/JPA, `BS-17`), только сгенерированные POJO/enum (`BS-18`). Схема версионируется в `migrations/db/changelog/v-1.0/` (Liquibase, `BS-10/12`); codegen из **применённой** схемы локального PG (`docker compose up -d postgres` → `./gradlew :adapter-out-postgres:regenerate` → `build`). Outbox-relay в `bootstrap` или `adapter-out-postgres` — `@Scheduled`, читает `outbox WHERE published_at IS NULL`, на Ф2 публикует в лог-стаб (`LoggingExternalEventPublisher`), Kafka-publisher подменит его в Ф4 (`BS-15`).

**Tech Stack:** PostgreSQL 16+, Liquibase 4.31, jOOQ 3.19 (`nu.studer.jooq` plugin — уже в `adapter-out-postgres/build.gradle.kts`), `spring-boot-starter-jooq`, HikariCP, Testcontainers (PG) для интеграционного теста, MapStruct (опц. для маппера).

**Где мы:** Ф0+Ф1 готовы. `adapter-out-postgres/build.gradle.kts` уже содержит конфиг jOOQ codegen (`nu.studer.jooq` 10.2, target package `ru.vikulinva.orderservice.adapter.out.postgres.generated`, PASCAL `_Pojo` matcher, excludes `databasechangelog*`) + Liquibase Gradle plugin (`changelogFile = "db/changelog-master.yaml"`, `searchPath = ${rootDir}/migrations`) + task `regenerate` (update + generateJooq) + `sourceSets.main.resources.srcDir(rootProject.file("migrations"))`. Сейчас `migrations/db/changelog-master.yaml` — пустой baseline `databaseChangeLog: []`; `adapter-out-postgres/src/main/java/` пуст. `core` содержит `OrderRepository` (interface) и `Order.fromPersistence(...)`. `application.yml`: `spring.liquibase.change-log: classpath:db/changelog-master.yaml`, `contexts: production`, `spring.datasource` → `jdbc:postgresql://localhost:5432/orders` (orders/orders), `spring.jooq.sql-dialect: postgres`. Roadmap — `docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md`.

---

### Task 1: Pre-flight

**Files:** —

- [ ] **Step 1: Состояние ветки + БД**

Run: `git status --short | grep -v '^??'` (чистое дерево), `git log --oneline -3` (ветка `feat/order-service-from-scratch`, HEAD на roadmap-Ф1). `docker compose up -d postgres` → дождаться healthy (`docker inspect -f '{{.State.Health.Status}}' order-service-postgres` == `healthy`). `psql postgresql://orders:orders@localhost:5432/orders -c '\dt'` (или через docker exec) — должно быть пусто (или только `databasechangelog*` от прошлых прогонов; если есть `orders` и пр. от старого репо — `docker compose down -v && docker compose up -d postgres` для чистого старта).

- [ ] **Step 2: Перечитать спеку**

`docs/spec/03-order-service-model.md` (§3.5 — схема БД, индексы), `09-order-service-queries.md` (Read Model `order_summaries`/`order_timelines` + индексы), `12-order-service-sagas.md` (`refund_sagas` статусы), `08-order-service-events.md` (`processed_events`, `outbox`), `07-order-service-commands.md` (`idempotency_keys`), `16-order-service-nfr.md` (PII `Address` шифрование, партиционирование `orders` по `created_at`). И `.claude/docs/{pg-types-style-guide,pg-naming-style-guide,pg-indexes-style-guide,jooq-rules,pg-runtime-style-guide}.md` — нужные секции по кодам.

---

### Task 2: Liquibase-схема — `/ucp-pg-schema-design` → `/ucp-pg-schema-review`

**Files (создаёт скилл):**
- Create: `migrations/db/changelog/v-1.0/initial-schema.yaml` (или несколько changeset-файлов в `v-1.0/`)
- Modify: `migrations/db/changelog-master.yaml` (заменить `databaseChangeLog: []` на `include`-список новых changeset'ов)

Таблицы (имена snake_case, типы по `pg-types-style-guide.md`): `orders` (PK `id uuid`, `customer_id uuid`, `status order_status` enum, `total_amount numeric(19,2)`, `currency varchar(3)`, `discount_amount numeric(19,2)` nullable, `discount_kind varchar` + `discount_percentage numeric` / `discount_fixed_amount numeric` — для sealed `Discount` (или JSONB колонка `discount jsonb` nullable — выбрать), `shipping_fee numeric(19,2)`, `shipping_country/city/street/postal_code/pickup_point_code` (text; шифрование at-rest — pgcrypto или app-level, по `16-nfr`; на Ф2 можно text + TODO Ф7, решит skill), `reservation_id uuid` nullable, `payment_id uuid` nullable, `paid_at/shipped_at/delivered_at/closed_at timestamptz` nullable, `created_at/updated_at timestamptz not null`); `order_items` (PK `id uuid`, FK `order_id uuid → orders(id) ON DELETE CASCADE`, `product_id uuid`, `seller_id uuid`, `quantity integer`, `unit_price numeric(19,2)`, UNIQUE `(order_id, product_id, seller_id)`); `outbox` (PK `id uuid`, `aggregate_id uuid`, `aggregate_type varchar`, `event_type varchar`, `payload jsonb`, `occurred_at timestamptz` (= event createdAt), `published_at timestamptz` nullable); `processed_events` (PK `event_id uuid`, `processed_at timestamptz not null`); `idempotency_keys` (PK `idempotency_key varchar`, `request_hash varchar`, `order_id uuid`, `created_at timestamptz`); `refund_sagas` (PK `id uuid`, `order_id uuid`, `status varchar` (`STARTED/INVENTORY_RELEASED/PAYMENT_REFUNDING/COMPLETED/FAILED`), `started_at/completed_at timestamptz`); `order_summaries` (PK `order_id uuid`, `customer_id uuid`, `primary_seller_id uuid`, `status order_status`, `total_amount numeric(19,2)`, `currency varchar(3)`, `items_count integer`, `first_product_title varchar`, `created_at/updated_at timestamptz`); `order_timelines` (PK `id uuid`, FK `order_id uuid`, `event_type varchar`, `occurred_at timestamptz`, `actor_type varchar`, `actor_id uuid` nullable, `metadata jsonb`); `order_audit_log` (PK `id uuid`, `order_id uuid`, `who varchar`, `what varchar`, `at timestamptz`, `details jsonb`). Enum `order_status` (отдельным changeset `v-1.0/enum-types.yaml`: `CREATE TYPE order_status AS ENUM ('DRAFT','PENDING_PAYMENT','PAID','SHIPPED','DELIVERED','COMPLETED','EXPIRED','CANCELLED','REFUNDED','DISPUTE')`). Индексы: `idx_orders_customer_status (customer_id, status)`, `idx_order_items_seller (seller_id)`, `idx_outbox_unpublished (occurred_at) WHERE published_at IS NULL`, `idx_summaries_customer (customer_id, status, created_at DESC)`, `idx_summaries_seller (primary_seller_id, status, created_at DESC)`, `idx_summaries_status_created (status, created_at)`, `idx_timeline_order_time (order_id, occurred_at)`, `idx_refund_sagas_order (order_id)`. `created_at/updated_at` — `DEFAULT now()`. (Партиционирование `orders` по `created_at` из `16-nfr` — отложить, не на Ф2; решит skill, по умолчанию не делаем.)

- [ ] **Step 1: Вызвать `/ucp-pg-schema-design`**

`Skill` tool, `skill=ucp-pg-schema-design`. Контекст: «Order Service, начальная схема (`v-1.0`), Liquibase (не Flyway), PostgreSQL 16. Список таблиц/колонок/индексов/enum — см. секцию Files и `docs/spec/03-order-service-model.md` §3.5, `09-queries.md` (Read Model), `12-sagas.md` (`refund_sagas`), `08-events.md` (`outbox`/`processed_events`), `07-commands.md` (`idempotency_keys`), `16-nfr.md` (PII `Address` — шифрование at-rest, реши: pgcrypto-колонки сейчас или text + отложить в Ф7). Деньги — `numeric(19,2)`, время — `timestamptz`, id — `uuid`, enum-колонки → Postgres `CREATE TYPE`. FK `order_items.order_id → orders(id) ON DELETE CASCADE`. Партиционирование `orders` — НЕ сейчас. Changeset'ы в `migrations/db/changelog/v-1.0/`, подключить `include`-ами в `migrations/db/changelog-master.yaml` (сейчас там `databaseChangeLog: []`). `searchPath`/`changelogFile` уже настроены в `adapter-out-postgres/build.gradle.kts` — не менять.» Следовать чек-листу скилла.

- [ ] **Step 2: Применить миграции к локальному PG**

Run: `./gradlew :adapter-out-postgres:update --console=plain` (Liquibase update против `jdbc:postgresql://localhost:5432/orders`).
Expected: `UPDATE SUMMARY` без ошибок; `\dt` в БД показывает все таблицы; `\dT` — тип `order_status`.

- [ ] **Step 3: Ревью схемы — `/ucp-pg-schema-review`**

`Skill` tool, `skill=ucp-pg-schema-review`. Контекст: «Ревью `migrations/db/changelog/v-1.0/*.yaml` и `changelog-master.yaml` против `pg-types-style-guide.md` / `pg-naming-style-guide.md`: типы колонок (деньги `numeric`, время `timestamptz` не `timestamp`, id `uuid` не `varchar(36)`, нет `varchar(255)`, нет `serial`/`float`), boolean, enum через `CREATE TYPE`, антипаттерны. Изменения — `git diff`.» Findings MUST — исправить (changeset ещё не применён в проде → можно править файлы; локальный PG пересоздать через `docker compose down -v && up` + повторить Step 2).

- [ ] **Step 4: Commit**

```bash
git add migrations
git commit -m "feat(Ф2): Liquibase initial schema v-1.0 — orders/order_items/outbox/processed_events/idempotency_keys/refund_sagas/order_summaries/order_timelines/order_audit_log + enum order_status

Сгенерировано /ucp-pg-schema-design по docs/spec/03/07/08/09/12/16, прошло /ucp-pg-schema-review.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: jOOQ codegen + `JooqOrderRepository` — `/ucp-jooq-design`

**Files (создаёт скилл, модуль `adapter-out-postgres`, package root `ru.vikulinva.orderservice.adapter.out.postgres`):**
- Generated (не в VCS, `build/generated/jooq/`): `…postgres.generated.tables.pojos.*Pojo`, `…generated.enums.OrderStatus`, table refs.
- Create: `…postgres/repository/JooqOrderRepository.java` — `@Repository`, `@RequiredArgsConstructor` (DSLContext + `DateTimeService` если нужен + outbox-writer + ObjectMapper для JSONB payload), реализует `ru.vikulinva.orderservice.domain.repository.OrderRepository`: `save(Order)` — upsert строки `orders` + replace `order_items` (delete+insert или merge) + для каждого `order.getEvents()` insert в `outbox` (`event_type` = simple class name, `payload` = JSON сериализация события, `occurred_at` = event `createdAt`, `published_at` = null), затем `order.clearDomainEvents()`; всё в рамках вызывающей `@Transactional` (handler управляет транзакцией, не репозиторий — `R-JOOQ-*`). `findById(OrderId)` / `findByIdForUpdate(OrderId)` — select `orders` + `order_items` (`multiset` или batch-fetch), map в `Order.fromPersistence(...)`; `forUpdate` → `SELECT … FOR UPDATE`. `delete(Order)`.
- Create: `…postgres/mapper/OrderDomainRecordMapper.java` — Plain Java (или MapStruct, по содержанию — реши через скилл): jOOQ-POJO/Record `↔` `Order`/`OrderItem`/VO (UUID→`OrderId`, `numeric`→`Money` (currency из колонки `currency`), `discount_*`/`discount jsonb` → `Discount` sealed, `shipping_*` → `Address`, enum `OrderStatus` (generated) ↔ domain `OrderStatus`).
- Create (опц.): `…postgres/outbox/OutboxAppender.java` (или метод в JooqOrderRepository) — insert событий в `outbox`. `…postgres/mapper/EventPayloadSerializer.java` — JSON сериализация `DomainEvent` через ObjectMapper (тот, что с `Visibility.ANY` из `JacksonConfig` Ф0 — должен быть бин, инжектится).

- [ ] **Step 1: Регенерация jOOQ-классов**

Run: `./gradlew :adapter-out-postgres:regenerate --console=plain` (= `update` + `generateJooq` против локального PG).
Expected: `build/generated/jooq/ru/vikulinva/orderservice/adapter/out/postgres/generated/` содержит `tables/`, `tables/pojos/`, `enums/OrderStatus.java`, `tables/records/`. `./gradlew :adapter-out-postgres:compileJava` — пока ещё пусто, но генерация не падает.

- [ ] **Step 2: Вызвать `/ucp-jooq-design`**

`Skill` tool, `skill=ucp-jooq-design`. Контекст: «Order Service, модуль `adapter-out-postgres`, package root `ru.vikulinva.orderservice.adapter.out.postgres`, generated jOOQ в `…postgres.generated.*` (POJO с суффиксом `_Pojo`, enum `OrderStatus`). Сгенерировать `JooqOrderRepository` (реализует `ru.vikulinva.orderservice.domain.repository.OrderRepository` из `core`: `save` — upsert `orders` + replace `order_items` + append доменных событий в `outbox` (event_type=simple name, payload=JSON, occurred_at=event createdAt, published_at=null) + `order.clearDomainEvents()`; `findById`/`findByIdForUpdate` — load `orders`+`order_items` через `multiset`, map в `Order.fromPersistence(...)`, forUpdate → `FOR UPDATE`; `delete`; `@Transactional` НЕ на репозитории — на handler'е), `OrderDomainRecordMapper` (jOOQ-POJO ↔ domain; `Money`(numeric+currency), `Discount` sealed из `discount_*`/`discount jsonb`, `Address` из `shipping_*`, generated `OrderStatus` ↔ domain `OrderStatus`), при необходимости `OrderFilterConditionBuilder` (для Ф5 read-queries — можно отложить), и `EventPayloadSerializer` для JSONB payload (ObjectMapper-бин с Visibility.ANY уже есть в bootstrap `JacksonConfig`, но в `adapter-out-postgres` нужно его получить — инжектить `ObjectMapper`). Доменные типы `Order`/`OrderItem`/VO — из `core`, см. их API. Не трогать REST/Kafka.» Следовать чек-листу скилла + `jooq-rules.md`.

- [ ] **Step 3: Сборка**

Run: `./gradlew :adapter-out-postgres:build --console=plain` (после `regenerate`).
Expected: BUILD SUCCESSFUL — `JooqOrderRepository` компилируется поверх generated-классов.

- [ ] **Step 4: Commit**

```bash
git add adapter-out-postgres
git commit -m "feat(Ф2): JooqOrderRepository + OrderDomainRecordMapper + outbox append

Сгенерировано /ucp-jooq-design. save() atomic: upsert orders + order_items + append доменных событий в outbox (JSONB payload) + clearDomainEvents; findByIdForUpdate → FOR UPDATE; @Transactional на handler'е (Ф3+), не на репозитории.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Outbox-relay — `/ucp-pg-runtime-design`

**Files (создаёт скилл):**
- Create: `core/.../port/out/ExternalEventPublisher.java` — интерфейс (`void publish(String aggregateId, String eventType, String payloadJson)` или с `OutboxRecord`-DTO), реализации: лог-стаб сейчас, Kafka в Ф4.
- Create: `bootstrap/.../outbox/LoggingExternalEventPublisher.java` — `@Component @ConditionalOnMissingBean(name="kafkaExternalEventPublisher")` (`BS-15`), логирует событие (`@Slf4j`).
- Create: `bootstrap/.../outbox/OutboxRelay.java` (или в `adapter-out-postgres`) — `@Component`, `@Scheduled(fixedDelayString = "${orderservice.outbox.poll-interval-ms:1000}")`, в `@Transactional`: `SELECT id, aggregate_id, event_type, payload, occurred_at FROM outbox WHERE published_at IS NULL ORDER BY occurred_at FOR UPDATE SKIP LOCKED LIMIT :batchSize` (jOOQ DSL), для каждого → `externalEventPublisher.publish(...)`, `UPDATE outbox SET published_at = now() WHERE id = …`. Свойства из `application.yml` (`orderservice.outbox.poll-interval-ms`, `batch-size` — уже есть в yml). `@EnableScheduling` — в `OrderServiceApplication` или отдельном `@Configuration` (проверить, нет ли уже; если нет — добавить).
- Create (опц.): метрика `outbox_lag_seconds` (Micrometer gauge: `now - min(occurred_at) WHERE published_at IS NULL`) — можно отложить в Ф7 observability; решить через скилл.

- [ ] **Step 1: Вызвать `/ucp-pg-runtime-design`**

`Skill` tool, `skill=ucp-pg-runtime-design`. Контекст: «Order Service. Нужен outbox-relay (publishing pattern): таблица `outbox` уже создана в Ф2 (`id uuid PK, aggregate_id uuid, aggregate_type varchar, event_type varchar, payload jsonb, occurred_at timestamptz, published_at timestamptz nullable`, индекс `idx_outbox_unpublished (occurred_at) WHERE published_at IS NULL`); записи туда кладёт `JooqOrderRepository.save()` (Ф2). Реализовать: port `ExternalEventPublisher` в `core/port/out/` (publish одного события из outbox); лог-стаб `LoggingExternalEventPublisher` в bootstrap (`@ConditionalOnMissingBean(name="kafkaExternalEventPublisher")`, BS-15 — Kafka-publisher подменит в Ф4); `OutboxRelay` `@Scheduled` (`SELECT … FOR UPDATE SKIP LOCKED LIMIT batch`, publish, `UPDATE published_at=now()`, всё в `@Transactional`); `@EnableScheduling`. Свойства `orderservice.outbox.{poll-interval-ms,batch-size}` уже в `application.yml`. На профилях `local`/`integration-test` шедулер может работать (poll-interval в integration-test = 1 день — фактически выключен; в local — 2с). Метрика `outbox_lag_seconds` — опционально, можно отложить в Ф7.» Следовать чек-листу + `pg-runtime-style-guide.md`.

- [ ] **Step 2: Сборка**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, ArchUnit hexagonal-тест PASS (новые классы: port в `core`, реализация publisher в `bootstrap`, relay в `bootstrap`/`adapter-out-postgres` — без нарушений слоёв).

- [ ] **Step 3: Commit**

```bash
git add core bootstrap adapter-out-postgres
git commit -m "feat(Ф2): Outbox-relay — ExternalEventPublisher port + LoggingExternalEventPublisher stub + OutboxRelay @Scheduled (SKIP LOCKED)

Сгенерировано /ucp-pg-runtime-design. Relay читает outbox WHERE published_at IS NULL (FOR UPDATE SKIP LOCKED), публикует в лог-стаб (Kafka-publisher подменит в Ф4, BS-15), помечает published_at.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Интеграционный smoke-тест save/load + outbox

**Files:**
- Create: `bootstrap/src/test/java/.../persistence/OrderPersistenceIntegrationTest.java` — `@SpringBootTest`, профиль `integration-test`, Testcontainers PG через `@ServiceConnection` (или `@DynamicPropertySource`), Liquibase прогоняется на контейнере. Тест: создать `Order.create(...)` + `addItem` + `confirm` → `orderRepository.save(order)` → `orderRepository.findById(id)` возвращает заказ с тем же статусом/items/total; в таблице `outbox` есть строки `OrderCreated` и `OrderConfirmed` с непустым `payload` (проверить, что payload содержит `customerId` / `totalAmount` — регрессия `BS-16`). `findByIdForUpdate` в `@Transactional` возвращает заказ.
- Modify (если нужно): `test-utils/src/main/java/.../testutil/base/PlatformBaseIntegrationTest.java` — базовый класс с Testcontainers PG (по `test-strategy.md`: только PG через Testcontainers, без Kafka/Redis). Можно создать минимальный сейчас; полноценно — Ф8 `/ucp-test-design`.

- [ ] **Step 1: Базовый класс интеграционных тестов (минимальный)**

Создать `test-utils/src/main/java/ru/vikulinva/orderservice/testutil/base/PlatformBaseIntegrationTest.java` (или как назовёт `test-strategy.md`): `@SpringBootTest`, `@ActiveProfiles("integration-test")`, `@Testcontainers`, статический `@Container PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")` с `@ServiceConnection` (Spring Boot 3.1+). Без Kafka/Redis. (Если `test-strategy.md` диктует другую структуру/имя — следовать ему; полная реализация `DatabasePreparer`/`TestObjectGenerator` — Ф8.)

- [ ] **Step 2: Написать тест**

`OrderPersistenceIntegrationTest extends PlatformBaseIntegrationTest`, `@Autowired OrderRepository orderRepository`, `@Autowired DSLContext dsl` (или `org.jooq.DSLContext`):
```java
@Test
void savesAndLoadsOrderWithItemsAndOutbox() {
    Order order = Order.create(OrderId.of(UUID.randomUUID()), CustomerId.of(UUID.randomUUID()),
        new Address("RU","Moscow","Tverskaya 1","101000",null), Money.rub(0), Instant.now());
    order.addItem(OrderItemId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SellerId.of(UUID.randomUUID()),
        Quantity.of(2), Money.rub(150), Instant.now());
    order.confirm(Instant.now());
    orderRepository.save(order);

    Order loaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(loaded.getItems()).hasSize(1);
    assertThat(loaded.total()).isEqualTo(Money.rub(300));

    // outbox: OrderCreated + OrderConfirmed, payload не пустой (BS-16)
    var rows = dsl.fetch("SELECT event_type, payload::text AS payload FROM outbox WHERE aggregate_id = ?", order.getId().value());
    assertThat(rows).hasSize(2);
    assertThat(rows.stream().anyMatch(r -> "OrderConfirmed".equals(r.get("event_type")))).isTrue();
    assertThat(rows.stream().allMatch(r -> r.get("payload", String.class).contains("totalAmount"))).isTrue();
}
```
(Уточнить точные имена generated-таблиц/колонок; можно через jOOQ DSL вместо raw SQL.)

- [ ] **Step 3: Прогнать тест**

Run: `docker compose up -d postgres` (если не запущен), `./gradlew :bootstrap:test --tests '*OrderPersistenceIntegrationTest*' --console=plain`
Expected: PASS. (Testcontainers поднимает свой PG-контейнер — `docker compose` PG для codegen, Testcontainers PG для теста; не конфликтуют, разные порты.)

- [ ] **Step 4: Commit**

```bash
git add test-utils bootstrap/src/test
git commit -m "test(Ф2): интеграционный smoke save/load Order + проверка outbox payload (Testcontainers PG)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Финальная проверка фазы + roadmap

**Files:**
- Modify: `docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md`

- [ ] **Step 1: Полная сборка + тесты**

Run: `docker compose up -d postgres`, `./gradlew :adapter-out-postgres:regenerate`, `./gradlew clean build` — но `clean` удалит `build/generated/jooq` → `compileJava` упадёт. Правильная последовательность: `./gradlew clean` → `:adapter-out-postgres:regenerate` → `build`. Либо: `./gradlew build` без `clean` (generated-классы на месте после Task 3). Expected: BUILD SUCCESSFUL, ArchUnit PASS, core unit-тесты + `OrderPersistenceIntegrationTest` зелёные.
**Записать в roadmap** workflow сборки: «`adapter-out-postgres` требует `docker compose up -d postgres` + `./gradlew :adapter-out-postgres:regenerate` перед первым `build` (jOOQ codegen из применённой Liquibase-схемы); generated-классы в `build/generated/jooq/` (gitignored)».

- [ ] **Step 2: Smoke `bootRun` на `local`**

Run: `docker compose up -d postgres`, `./gradlew :bootstrap:bootRun --args='--spring.profiles.active=local --server.port=18080'`, дождаться `Started OrderServiceApplication`, проверить в логах что Liquibase накатил `v-1.0` (`UPDATE SUMMARY` с changeset'ами), что `OutboxRelay` стартовал (нет ошибок), `curl /actuator/health` = UP; остановить, `docker compose down`.

- [ ] **Step 3: Отметить Ф2 в roadmap + commit**

В roadmap раздел «Статус»: «Ф2 — ГОТОВА» + коммиты + workflow сборки; в заголовке «### Ф2 — Persistence + Outbox-relay» добавить ✅; поправить устаревшие упоминания «Flyway»/«persistence» в описании Ф2-Ф8 при случае.
```bash
git add docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md
git commit -m "docs: roadmap — Ф2 готова, workflow сборки adapter-out-postgres

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Чекпоинт с пользователем**

Доложить итог Ф2, спросить — переходим к Ф3 (Happy-path команды UC-1: `/ucp-api-design` → `/ucp-pattern-design` для CreateOrder/AddItem/RemoveItem/ApplyPromo/RemovePromo/ConfirmOrder → `/ucp-integration-design` для Catalog → `/ucp-error-handling-design` → `/ucp-pattern-review` + `/ucp-api-review` → `/ucp-test-design`) через новый `superpowers:writing-plans`, или пауза.

---

## Self-review

- **Покрытие Ф2 из roadmap:** Task 2 = `/ucp-pg-schema-design` + `/ucp-pg-schema-review` (все 9 таблиц + enum + индексы из `03-model`/`09-queries` ✓, шифрование `Address` — решение делегировано skill'у с дефолтом «text + отложить в Ф7» ✓); Task 3 = jOOQ codegen + `/ucp-jooq-design` (`JooqOrderRepository` с atomic save + outbox append + `findByIdForUpdate` ✓, `OrderDomainRecordMapper` ✓); Task 4 = `/ucp-pg-runtime-design` (outbox-relay `@Scheduled` SKIP LOCKED ✓); Task 5 = интеграционный smoke (save/load + outbox payload regression BS-16 ✓); Task 6 = выход фазы ✓. Пробелов нет. `OrderFilterConditionBuilder` / read-view-репозитории для Ф5 — упомянуты как «можно отложить» (Ф5 их доделает).
- **Плейсхолдеры:** код persistence генерируют `/ucp-pg-schema-design` / `/ucp-jooq-design` / `/ucp-pg-runtime-design` по чек-листам — намеренное делегирование (CLAUDE.md); конкретика — в списках Files и детальных промптах Step 1 каждой задачи. Task 5 содержит готовый skeleton теста (точные имена generated-таблиц уточняются при исполнении — отмечено явно).
- **Согласованность:** package root `ru.vikulinva.orderservice.adapter.out.postgres`, generated package `…postgres.generated.*` (как в restored `build.gradle.kts`), таблица `outbox` (колонки `aggregate_id/aggregate_type/event_type/payload/occurred_at/published_at`) — одинаково в Task 2/3/4/5; `OrderRepository` (из `core`, Ф1) — реализуется в Task 3; `Order.fromPersistence(...)` (Ф1) — используется в Task 3; `ExternalEventPublisher` — Task 4 создаёт, Ф4 подменит Kafka-реализацией.
- **Риски:** (1) `clean build` ломает jOOQ-codegen workflow — отмечено в Task 6 Step 1; (2) MapStruct vs Plain Java mapper — решение делегировано `/ucp-jooq-design`; (3) `OrderConfirmed` payload поле — тест проверяет `totalAmount` (фактическое имя поля события, не `total` из spec-контракта) — расхождение спека↔либа отмечено как долг Ф1, сверим в Ф4; (4) `discount` маппинг (sealed `Discount` → колонки vs JSONB) — решение делегировано `/ucp-pg-schema-design` + `/ucp-jooq-design`.
