package com.domain.taskflow.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEvent {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "job_id", columnDefinition = "uuid", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;

    public JobEvent(UUID id, UUID jobId, String eventType, String payload) {
        this.id = id;
        this.jobId = jobId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}
