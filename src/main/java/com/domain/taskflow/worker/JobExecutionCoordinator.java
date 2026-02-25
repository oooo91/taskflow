package com.domain.taskflow.worker;

import com.domain.taskflow.metrics.JobMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobExecutionCoordinator {

    private final RedisLockService lockService;
    private final JobMetrics jobMetrics;
    private final WorkerRunner workerRunner;
    private final WorkerProperties workerProperties;

    public void runOneWithLock(UUID jobId) {
        String workerId = workerProperties.getId();
        String lockKey = lockService.jobLockKey(jobId.toString());

        Duration ttl = Duration.ofMillis(workerProperties.getLockLeaseMs());
        boolean locked = lockService.tryLock(lockKey, workerId, ttl);
        if (!locked) return;

        try {
            // Job 실행 전체 시간 측정
            jobMetrics.timer().record(() -> workerRunner.runOne(jobId));
        } finally {
            lockService.unlock(lockKey, workerId);
        }
    }
}
