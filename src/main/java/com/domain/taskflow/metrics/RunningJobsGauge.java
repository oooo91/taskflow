package com.domain.taskflow.metrics;

import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RunningJobsGauge {

    private final JobRepository jobRepository;
    private final AtomicLong running = new AtomicLong(0);

    public RunningJobsGauge(MeterRegistry registry, JobRepository jobRepository) {
        this.jobRepository = jobRepository;

        Gauge.builder("taskflow_jobs_running", running, AtomicLong::get)
                .description("실행 중인 작업 수")
                .register(registry);
    }
    @Scheduled(fixedDelayString = "${taskflow.metrics.running-gauge-interval-ms:1000}")
    public void tick () {
        long cnt = jobRepository.countByStatus(JobStatus.RUNNING);
        running.set(cnt);
    }
}
