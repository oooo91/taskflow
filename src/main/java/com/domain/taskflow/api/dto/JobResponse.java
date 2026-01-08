package com.domain.taskflow.api.dto;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class JobResponse {
    public UUID id;
    public String jobKey;
    public String type;
    public String payload;
    public JobStatus status;
    public OffsetDateTime scheduledAt;
    public boolean cancelRequested;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;

    public static JobResponse from(Job job) {
        JobResponse r = new JobResponse();
        r.id = job.getId();
        r.jobKey = job.getJobKey();
        r.type = job.getType();
        r.payload = job.getPayload();
        r.status = job.getStatus();
        r.scheduledAt = job.getScheduledAt();
        r.cancelRequested = job.isCancelRequested();
        r.createdAt = job.getCreatedAt();
        r.updatedAt = job.getUpdatedAt();
        return r;
    }
}
