# Order Service — эталонная реализация Use Case Pattern (Tier C / Level 3 DDD)

Этот репозиторий — один из сервисов сквозного кейса маркетплейса с
[vikulin-va.ru](https://vikulin-va.ru/case/), реализованный по методологии
[Use Case Pattern](https://vikulin-va.ru/use-case-pattern/) на уровне зрелости 3
(DDD).

Артефакт состоит из двух частей:

1. **Use Case спецификация** — `docs/spec/`, в формате 16+1 разделов
   ([шаблон](https://vikulin-va.ru/use-case-pattern/spec-template/)). Это
   контракт между бизнесом и разработкой; машинно-читаемые теги в фронт-маттере
   позволяют скриптам строить C4-диаграммы и проверять полноту.
2. **Код** — Spring Boot модуль (будет наполнен скиллами по спецификации). См.
   ниже план генерации.

## Структура спецификации

```
docs/spec/
    order-service.md                    # индекс (type: service)
    01-order-service-context.md         # Bounded Context + диаграмма C1
    02-order-service-language.md        # глоссарий
    03-order-service-model.md           # агрегаты, VO, схема БД
    04-order-service-lifecycle.md       # state machine
    05-order-service-roles.md           # RBAC + ABAC
    06-order-service-rules.md           # бизнес-правила (BR-001..BR-015)
    07-order-service-commands.md        # UseCaseCommand-ы
    08-order-service-events.md          # доменные события
    09-order-service-queries.md         # UseCaseQuery + Read Model
    10-order-service-use-cases.md       # сквозные сценарии
    11-order-service-ui.md              # экраны и тексты ошибок
    12-order-service-sagas.md           # Saga «Confirm Order» и «Process Refund»
    13-order-service-errors.md          # каталог ошибок (RFC 9457)
    14-order-service-integrations/      # рёбра C2 (по файлу на интеграцию)
    15-order-service-acceptance.md      # acceptance criteria + покрытие тестами
    16-order-service-nfr.md             # производительность, SLA, security
    17-order-service-stack.md           # технологический стек
```

## Машинно-читаемые теги

Каждый файл начинается с YAML фронт-маттера. Ключевые поля:

- **Сервис (`order-service.md`):** `type: service`, `tier: C`, `ucp-level: 3`,
  `tags: [tech/*, ucp/level-3, tier/C, bc/order]`.
- **Раздел:** `type: context-section`, `context: order-service`,
  `parent: "[[order-service]]"`, `section: <key>`.
- **Интеграция:** `type: integration`, `source: "[[a]]"`, `target: "[[b]]"`,
  `direction: inbound|outbound`, `protocol: rest|kafka`, `sync: sync|async`,
  `ddd-pattern: [...]`.

Это совместимо с Obsidian (для редактирования) и с Python-скриптом для
построения C4 (`tools/c4.py` будет добавлен).

## План генерации кода

Из этой спецификации скиллы из
[`usecase-pattern-skills`](https://github.com/remodov/usecase-pattern-skills)
генерируют артефакты:

| Источник в спеке | Скилл | Получаем |
|---|---|---|
| §3 Domain Model | `/ddd-tactical-design` | Aggregate `Order`, Entity `OrderItem`, Value Objects (`Money`, `Quantity`, `Discount`, `OrderId`, …), Domain Events, Repository |
| §7 Commands | `/usecase-pattern-design` | `*UseCase` (records) и `*UseCaseHandler` (`@Component`) |
| §9 Queries | `/usecase-pattern-design` | `*Query`, `*QueryHandler`, MapStruct мапперы для Read Model |
| §7 + §11 + §13 | `/api-design` | OpenAPI 3 спека для всех endpoints |
| любой код | `/usecase-pattern-review`, `/ddd-tactical-review` | findings по соответствию правилам |

## Использование как референса

Если ты строишь **свой** сервис по той же методологии — клонируй структуру
`docs/spec/` и заполняй разделы по [шаблону](https://vikulin-va.ru/use-case-pattern/spec-template/).
Подключи `.claude/skills/` симлинком на
[`usecase-pattern-skills`](https://github.com/remodov/usecase-pattern-skills) —
скиллы будут читать твою спеку и генерировать код.

## Связанные артефакты

- [Use Case Pattern: методология](https://vikulin-va.ru/use-case-pattern/)
- [Use Case спецификация: шаблон](https://vikulin-va.ru/use-case-pattern/spec-template/)
- [Уровень зрелости 3: DDD](https://vikulin-va.ru/use-case-pattern/level-3/)
- [Тактические паттерны DDD](https://vikulin-va.ru/domain-driven-design/tactical-patterns/)
- [Распределённые паттерны](https://vikulin-va.ru/distributed-patterns/) (Outbox, Saga)
- Бизнес-кейс маркетплейса — [vikulin-va.ru/case/](https://vikulin-va.ru/case/)
- Библиотеки:
  - [`usecase-pattern`](https://github.com/remodov/usecase-pattern) (UseCase + Handler + Dispatcher)
  - [`ddd-building-blocks`](https://github.com/remodov/ddd-building-blocks) (Entity, AggregateRoot, ValueObject, DomainEvent)
- Скиллы AI-агента: [`usecase-pattern-skills`](https://github.com/remodov/usecase-pattern-skills)

## Лицензия

MIT.
