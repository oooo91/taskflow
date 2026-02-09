package com.domain.taskflow.domain;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED,
    RETRY_WAIT
}
