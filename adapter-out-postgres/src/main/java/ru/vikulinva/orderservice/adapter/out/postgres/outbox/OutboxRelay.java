package ru.vikulinva.orderservice.adapter.out.postgres.outbox;

import static ru.vikulinva.orderservice.adapter.out.postgres.generated.Tables.OUTBOX;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.orderservice.adapter.out.postgres.generated.tables.records.OutboxRecord;
import ru.vikulinva.orderservice.port.out.ExternalEventPublisher;

/**
 * Outbox-relay: периодически вычитывает неопубликованные строки {@code outbox}
 * ({@code SELECT … FOR UPDATE SKIP LOCKED LIMIT batch}), публикует через {@link ExternalEventPublisher},
 * проставляет {@code published_at}. Всё в одной транзакции (at-least-once: при сбое после publish и до
 * commit строка переотправится). Несколько реплик не мешают друг другу за счёт {@code SKIP LOCKED}.
 */
@Component
@Slf4j
public class OutboxRelay {

    private final DSLContext dsl;
    private final ExternalEventPublisher publisher;
    private final int batchSize;

    public OutboxRelay(DSLContext dsl, ExternalEventPublisher publisher,
                       @Value("${orderservice.outbox.batch-size:100}") int batchSize) {
        this.dsl = dsl;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderservice.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        List<OutboxRecord> batch = dsl.selectFrom(OUTBOX)
            .where(OUTBOX.PUBLISHED_AT.isNull())
            .orderBy(OUTBOX.OCCURRED_AT.asc())
            .limit(batchSize)
            .forUpdate()
            .skipLocked()
            .fetch();
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxRecord record : batch) {
            JSONB payload = record.getPayload();
            publisher.publish(new ExternalEventPublisher.OutboxMessage(
                record.getId(), record.getAggregateId(), record.getAggregateType(), record.getEventType(),
                payload == null ? null : payload.data(), record.getOccurredAt().toInstant()));
            dsl.update(OUTBOX)
                .set(OUTBOX.PUBLISHED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(OUTBOX.ID.eq(record.getId()))
                .execute();
        }
        log.debug("[outbox-relay] published {} event(s)", batch.size());
    }
}
