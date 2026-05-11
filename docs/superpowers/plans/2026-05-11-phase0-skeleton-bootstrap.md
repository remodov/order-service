# Ф0 — Skeleton & bootstrap — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **ВАЖНО (из CLAUDE.md):** шаги, ссылающиеся на `/ucp-*` скилл, исполняются **вызовом этого скилла через `Skill` tool в текущей сессии** — не пересказом его инструкций, не рукописным кодом, не `Agent`/`Task`-форком. Скиллы `/ucp-*` запускаются последовательно, не параллельно.

**Goal:** Поднять пустой, но собирающийся и запускающийся каркас Order Service — multi-module Gradle hexagonal skeleton + Spring Boot bootstrap (профили, Flyway, jOOQ codegen, Security per profile, Kafka-гейтинг).

**Architecture:** Hexagonal multi-module: `core` (домен, без Spring/jOOQ) ← `persistence` + `adapter-in-rest` + `adapter-in-kafka` + `adapter-out-catalog` + `adapter-out-payment` + `adapter-out-kafka`, всё собирается в `bootstrap` (Spring Boot app). На этой фазе модули пустые (placeholder-классы + `package-info.java`), бизнес-логики нет — только то, что нужно для зелёного `build` и поднимающегося `bootRun` на профиле `local`.

**Tech Stack:** Java 21, Spring Boot 3.x, Gradle Kotlin DSL, jOOQ 3.19+, Flyway, PostgreSQL 16+, Spring Kafka, Spring Security OAuth2 Resource Server, `ru.vikulinva:usecase-pattern-starter`, `ru.vikulinva:ddd-building-blocks`, ArchUnit, JUnit 5 + Testcontainers.

**Контекст репозитория:** рабочая директория зачищена (на диске только `CLAUDE.md`, `docs/`, `gradle/`-wrapper); предыдущая реализация в git HEAD игнорируется — строим заново. Источник правды — `docs/spec/`. Roadmap — `docs/superpowers/specs/2026-05-11-order-service-implementation-roadmap-design.md`.

---

### Task 1: Pre-flight — убедиться, что `/ucp-*` скиллы установлены

**Files:** —

- [ ] **Step 1: Проверить наличие скиллов**

Run: `ls -la .claude/skills/ | head` и `ls .claude/docs/`
Expected: симлинки на `ucp-hexagonal-design`, `ucp-bootstrap-design`, … и style-guides (`java-style-guide.md`, `usecase-pattern-style-guide.md`, …). Если пусто или broken-симлинки — выполнить bootstrap по README репо скиллов (`git pull` в `~/IdeaProjects/claude-code-java`, затем `~/IdeaProjects/claude-code-java/install.sh .`) и повторить.

- [ ] **Step 2: Зафиксировать стартовое состояние worktree**

Run: `git status --short | head` и `ls`
Expected: на диске `CLAUDE.md`, `docs/`, `gradle/`, `gradlew`, `gradlew.bat`; куча `D`-файлов (старая реализация, не трогаем). Ветка — `feat/order-service-from-scratch`.

---

### Task 2: Hexagonal multi-module skeleton — `/ucp-hexagonal-design`

**Files (создаёт скилл):**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`
- Create: `core/build.gradle.kts`, `core/src/main/java/ru/vikulinva/orderservice/order/{aggregate,port}/package-info.java`
- Create: `persistence/build.gradle.kts`
- Create: `adapter-in-rest/build.gradle.kts`, `adapter-in-kafka/build.gradle.kts`
- Create: `adapter-out-catalog/build.gradle.kts`, `adapter-out-payment/build.gradle.kts`, `adapter-out-kafka/build.gradle.kts`
- Create: `bootstrap/build.gradle.kts`, `bootstrap/src/main/java/ru/vikulinva/orderservice/Application.java`
- Create: `bootstrap/src/test/java/.../ArchitectureRulesTest.java` (ArchUnit base)

- [ ] **Step 1: Вызвать скилл**

Вызвать `Skill` tool с `skill=ucp-hexagonal-design`. Контекст для скилла: «Order Service, Tier C / UCP Level 3, package root `ru.vikulinva.orderservice`. In-adapter модули: `adapter-in-rest` (Customer/Seller/Admin BFF), `adapter-in-kafka` (Inventory + Payment events). Out-adapter модули: `persistence` (PostgreSQL+jOOQ), `adapter-out-catalog` (REST sync), `adapter-out-payment` (REST out), `adapter-out-kafka` (publisher из outbox). Bootstrap — `bootstrap`. Bounded context — `order` (агрегат `Order`). Стек строго по `docs/spec/17-order-service-stack.md`: jOOQ + Flyway (не Liquibase), `ru.vikulinva:usecase-pattern-starter`, `ru.vikulinva:ddd-building-blocks`, MapStruct, Resilience4j, Spring Kafka, Spring Security OAuth2 RS.» Следовать чек-листу скилла.

- [ ] **Step 2: Собрать**

Run: `./gradlew build -x test` (или `./gradlew compileJava` если скилл ещё не настроил всё)
Expected: BUILD SUCCESSFUL. Все модули компилируются. Если падает на отсутствии артефактов `ru.vikulinva:*` — проверить, в каком репозитории они лежат (`repositories {}` в root `build.gradle.kts`); это может потребовать настройки в Task 3 (bootstrap-скилл) — тогда отложить полную сборку до Task 4.

- [ ] **Step 3: Прогнать ArchUnit base test**

Run: `./gradlew :bootstrap:test --tests '*ArchitectureRules*'`
Expected: PASS (на пустом скелете правила тривиально выполняются).

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties core persistence adapter-in-rest adapter-in-kafka adapter-out-catalog adapter-out-payment adapter-out-kafka bootstrap
git commit -m "feat: hexagonal multi-module skeleton (core + adapters + bootstrap)

Сгенерировано /ucp-hexagonal-design по docs/spec/17-order-service-stack.md.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Spring Boot bootstrap — `/ucp-bootstrap-design`

**Files (создаёт/правит скилл):**
- Modify: `bootstrap/build.gradle.kts` (jOOQ codegen plugin, Flyway, Spring Boot plugin)
- Create: `bootstrap/src/main/resources/application.yml` (+ `application-local.yml`, `application-integration-test.yml`, `application-production.yml` или профильные секции)
- Create: `bootstrap/src/main/resources/db/migration/V1__baseline.sql` (пустой baseline / минимальная таблица — детальную схему делает Ф2)
- Create: `bootstrap/src/main/java/.../config/` — `ClockConfig` (production `Clock`), `UuidConfig` (production UUID-провайдер интерфейс), `SecurityConfig` (per profile: `local` — permitAll/без Keycloak, `production` — OAuth2 RS), Kafka-гейтинг (`@ConditionalOnProperty` на листенерах), Jackson-конфиг для видимости event-payload.
- Modify: `bootstrap/src/main/java/.../Application.java` при необходимости

- [ ] **Step 1: Вызвать скилл**

Вызвать `Skill` tool с `skill=ucp-bootstrap-design`. Контекст: «Order Service. Профили: `local` (без живых Keycloak/Kafka — Security permitAll или mock-jwt, Kafka-листенеры выключены, БД — локальный PG или Testcontainers-режим по вкусу), `integration-test` (Testcontainers PG + WireMock, Kafka выключен), `production` (OAuth2 Resource Server с JWK от Keycloak, Kafka включён). Миграции — Flyway (`db/migration`), jOOQ codegen из живой схемы (БД поднимается до codegen — Testcontainers-task или локальный PG). Persistence-слой — только на сгенерированных jOOQ-типах, без JdbcTemplate/JPA. Production-бины для `Clock` и UUID-провайдера. Гейтинг Kafka-листенеров по профилю. На Ф0 схема — пустой baseline; реальные таблицы в Ф2.» Следовать чек-листу скилла.

- [ ] **Step 2: Сборка целиком**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, включая jOOQ codegen (если codegen требует БД — должен подняться Testcontainers/локальный PG в рамках gradle-таски). Тесты (пока только ArchUnit + возможный smoke context-test) зелёные.

- [ ] **Step 3: Поднять приложение на профиле `local`**

Run: `SPRING_PROFILES_ACTIVE=local ./gradlew :bootstrap:bootRun` (или эквивалент), подождать старта, убедиться что контекст поднялся без `UnsatisfiedDependencyException` / без попыток фетча JWK / без падения на отсутствии Kafka; затем остановить (Ctrl-C).
Expected: в логах `Started Application`, actuator `/actuator/health` отвечает `UP` (если actuator подключён bootstrap-скиллом). Нет ERROR-логов про Keycloak/Kafka.

- [ ] **Step 4: Commit**

```bash
git add bootstrap
git commit -m "feat: Spring Boot bootstrap — профили local/integration-test/production, Flyway, jOOQ codegen, Security per profile

Сгенерировано /ucp-bootstrap-design.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Финальная проверка фазы + опциональное ревью

**Files:** —

- [ ] **Step 1: Полная сборка + тесты**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. Все модули компилируются, ArchUnit и smoke-тесты зелёные.

- [ ] **Step 2: Smoke `bootRun` на `local`** (повтор Task 3 Step 3 после `clean`)

Expected: контекст поднимается, `/actuator/health` = `UP`.

- [ ] **Step 3: (опционально) `/ucp-hexagonal-review`**

Скелет сгенерирован design-скиллом по чек-листу — авто-ревью не обязателен (см. CLAUDE.md). Если хочется — вызвать `Skill` tool с `skill=ucp-hexagonal-review` на изменённых файлах (`git diff main --stat`). Если ревью находит расхождения — исправить **через повторный вызов соответствующего design-скилла**, не вручную.

- [ ] **Step 4: Зафиксировать выход фазы**

Записать в roadmap-doc (или в новый файл `docs/superpowers/specs/`) отметку «Ф0 готова», и решить с пользователем — переходим к `superpowers:writing-plans` для Ф1 (Домен `Order`, `/ucp-ddd-tactical-design`).

---

## Self-review

- **Покрытие Ф0 из roadmap:** Task 2 = `/ucp-hexagonal-design` (multi-module skeleton ✓), Task 3 = `/ucp-bootstrap-design` (профили, Flyway, jOOQ codegen, Security, Kafka-гейтинг, Jackson, Clock/UUID-бины ✓), Task 4 = выход фазы (`build` зелёный, `bootRun` на `local` ✓). Пробелов нет.
- **Плейсхолдеры:** код руками тут не пишется намеренно — содержимое модулей генерируют `/ucp-*` скиллы по своим чек-листам (так требует CLAUDE.md); конкретика — в контекстных промптах для скиллов в Step 1 каждой задачи. Это не «TODO/fill in later», а делегирование исполнителю-скиллу.
- **Согласованность:** имена модулей (`core`/`persistence`/`adapter-in-rest`/`adapter-in-kafka`/`adapter-out-catalog`/`adapter-out-payment`/`adapter-out-kafka`/`bootstrap`) и package root (`ru.vikulinva.orderservice`) одинаковы во всех задачах и в roadmap-doc. Профили (`local`/`integration-test`/`production`) — тоже.
- **Риск:** если артефакты `ru.vikulinva:usecase-pattern-starter` / `ddd-building-blocks` недоступны в публичных репозиториях — `/ucp-hexagonal-design` / `/ucp-bootstrap-design` должны прописать нужный `repositories {}` блок; если артефактов нет совсем — это блокер, эскалировать пользователю (где лежат библиотеки).
