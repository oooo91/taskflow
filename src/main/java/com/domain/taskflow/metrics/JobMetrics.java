package com.domain.taskflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobMetrics {

    private final MeterRegistry registry;

    private final Counter jobCreated;
    private final Counter jobSucceeded;
    private final Counter jobFailed;
    private final Counter jobRetried;
    private final Counter staleReaped;

    // runOne() 전체 타이머
    private final Timer jobRunTimer;

    // attempt 처리 시간 타이머 (type/result 조합별로 캐시)
    private final ConcurrentHashMap<String, Timer> processingTimer = new ConcurrentHashMap<>();

    public JobMetrics(MeterRegistry registry) {
        this.registry = registry;

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

    /**
     * attempt 처리 시간 기록
     * prometheus - taskflow_job_processing_seconds_{count,sum,max} 로 노출됨
     *
     * @param jobType
     * @param result
     * @param duration
     */
    public void recordProcessing(String jobType, String result, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) return;

        String key = jobType + "|" + result;
        Timer t = processingTimer.computeIfAbsent(key, k ->
                Timer.builder("taskflow_job_processing")
                        .description("RUNING에서 커밋을 완료하기까지 처리 시간")
                        .tag("type", jobType == null ? "UNKNOWN" : jobType)
                        .tag("result", result == null ? "UNKNOWN" : result)
                        .register(registry)
        );

        t.record(duration);
    }
}
