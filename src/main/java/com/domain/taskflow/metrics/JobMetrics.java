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

    // retry 스케줄(= RETRY_WAIT로 전환된 횟수, 1 job이 여러 번 들어갈 수 있음)
    private final Counter jobRetryScheduled;

    // retry 대상 job 수 (job당 1회만 카운트)
    private final Counter jobRetryJobs;

    // retry 후 성공 job 수 (job당 1회만 카운트)
    private final Counter jobRetrySuccessJobs;

    // retry 후 최종 실패 job 수 (job당 1회만 카운트)
    private final Counter jobRetryFailedJobs;

    private final Counter staleReaped;

    // runOne() 전체 타이머
    private final Timer jobRunTimer;

    // attempt 처리 시간 타이머 (type/result 조합별로 캐시)
    private final ConcurrentHashMap<String, Timer> processingTimer = new ConcurrentHashMap<>();

    // stale 회수 "지연(overshoot)" 타이머 (outcome 태그별로 분리)
    private final ConcurrentHashMap<String, Timer> staleOvershootTimers = new ConcurrentHashMap<>();

    public JobMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.jobCreated = registry.counter("taskflow_job_created");
        this.jobSucceeded = registry.counter("taskflow_job_succeeded");
        this.jobFailed = registry.counter("taskflow_job_failed");

        this.jobRetryScheduled = registry.counter("taskflow_job_retry_scheduled");
        this.jobRetryJobs = registry.counter("taskflow_job_retry_jobs");
        this.jobRetrySuccessJobs = registry.counter("taskflow_job_retry_success_jobs");
        this.jobRetryFailedJobs = registry.counter("taskflow_job_retry_failed_jobs");

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
        jobRetryScheduled.increment();
    }

    public void incRetryJobs() {
        jobRetryJobs.increment();
    }

    public void incRetrySuccessJobs() {
        jobRetrySuccessJobs.increment();
    }

    public void incRetryFailedJobs() {
        jobRetryFailedJobs.increment();
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

    /**
     * stale 회수 지연(overshoot) 메트릭 추가
     * @param overshoot
     * @param outcome
     */
    public void recordStaleReapOvershoot(Duration overshoot, String outcome) {
        Timer t = staleOvershootTimers.computeIfAbsent(outcome, o ->
                Timer.builder("taskflow_stale_reap_overshoot")
                        .description("리퍼가 staleRunningMs를 초과한 오래된 작업을 회수한 시간")
                        .tag("outcome", o) // RETRY_WAIT or FAILED
                        .publishPercentileHistogram()
                        .register(registry)
        );
        t.record(overshoot);
    }
}
