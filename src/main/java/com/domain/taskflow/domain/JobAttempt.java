package com.domain.taskflow.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobAttempt {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "job_id", columnDefinition = "uuid", nullable = false)
    private UUID jobId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobAttemptStatus status;

    private String workerId;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column(nullable = false)
    private OffsetDateTime endedAt;

    private String errorCode;

    @Column(columnDefinition = "text")
    private String errorMessage;

    public JobAttempt(UUID id, UUID jobId, int attemptNo, String workerId) {
        this.id = id;
        this.jobId = jobId;
        this.attemptNo = attemptNo;
        this.workerId = workerId;
        this.status = JobAttemptStatus.RUNNING;
        this.startedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public JobAttemptStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markSuccess() {
        this.status = JobAttemptStatus.SUCCESS;
        this.endedAt = OffsetDateTime.now();
    }

    public void markFiled(String errorCode, String errorMessage) {
        this.status = JobAttemptStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.endedAt = OffsetDateTime.now();
    }

}
