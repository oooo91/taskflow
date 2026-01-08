package com.domain.taskflow.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "job_key", unique = true)
    private String jobKey;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    private OffsetDateTime scheduledAt;

    @Column(nullable = false)
    private boolean cancelRequested = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    public Job(UUID id, String jobKey, String type, String payload, OffsetDateTime scheduledAt) {
        this.id = id;
        this.jobKey = jobKey;
        this.type = type;
        this.payload = payload;
        this.status = JobStatus.PENDING;
        this.scheduledAt = scheduledAt;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void requestCancel() {
        // PENDING 이면 즉시 CANCELED, RUNNING 이면 cancelRequested=true
        if (this.status == JobStatus.PENDING) {
            this.status = JobStatus.CANCELED;
        } else if (this.status == JobStatus.RUNNING) {
            this.cancelRequested = true;
        } else {
            // 이미 종료 상태면 no-op 또는 예외 발생
        }
        touch();
    }

}
