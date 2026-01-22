package com.domain.taskflow.outbox;

import com.domain.taskflow.sse.SseHub;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final SseHub sseHub;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void tick() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1) unpublished batch 가져오기
        // (단일 인스턴스 기준)
        List<OutboxRow> rows = jdbcTemplate.query("""
                    select seq, event_type, payload
                      from job_events
                     where published_at is null
                     order by seq asc
                     limit 200
                """, (rs, i) -> new OutboxRow(
                rs.getLong("seq"),
                rs.getString("event_type"),
                rs.getString("payload")
        ));

        if (rows.isEmpty()) return;

        // 2) published_at 업데이트 (이 시점부터 "발행 완료"로 간주)
        // seq 범위 업데이트로 간단히 처리
        long minSeq = rows.get(0).seq;
        long maxSeq = rows.get(rows.size() - 1).seq;

        jdbcTemplate.update("""
                    update job_events
                       set published_at = ?
                     where published_at is null
                       and seq between ? and ?
                """, now, minSeq, maxSeq);

        // 3) SSE broadcast
        for (OutboxRow r : rows) {
            sseHub.broadcast(r.seq, r.eventType, r.payloadJson);
        }
    }

    private record OutboxRow(long seq, String eventType, String payloadJson) {
    }

}
