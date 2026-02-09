package com.domain.taskflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {

    private final Counter jobCreated;
    private final Counter jobSucceeded;
    private final Counter jobFailed;
    private final Counter jobRetried;
    private final Counter staleReaped;

    private final Timer jobRunTimer;

    public JobMetrics(MeterRegistry registry) {
        this.jobCreated = registry.counter("taskflow_job");
        this.jobSucceeded = registry.counter("taskflow_job_succeeded");
        this.jobFailed = registry.counter("taskflow_job_failed");
        this.jobRetried = registry.counter("taskflow_job_retry_scheduled");
        this.staleReaped = registry.counter("taskflow_job_stale_reaped");

        this.jobRunTimer = registry.timer("taskflow_job_run_seconds");
    }

    public void incCreated() {
        jobCreated.increment();
    }

    public void incSucceeded() {
        jobSucceeded.increment();
    }

    public void incFailed() {
        jobFailed.increment();
    }

    public void incRetryScheduled() {
        jobRetried.increment();
    }

    public void incStaleReaped() {
        staleReaped.increment();
    }

    public Timer timer() {
        return jobRunTimer;
    }
}
