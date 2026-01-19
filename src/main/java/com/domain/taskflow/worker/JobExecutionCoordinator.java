package com.domain.taskflow.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobExecutionCoordinator {

    private final RedisLockService lockService;
    private final JobRunner jobRunner;

    private final String workerId = "worker-1"; // 클라우드 올릴 떈 host/pod 로 변경

    public void runOneWithLock(UUID jobId) {
        String lockKey = lockService.jobLockKey(jobId.toString());
        boolean locked = lockService.tryLock(lockKey, workerId, Duration.ofSeconds(30));
        if (!locked) return;

        try {
            jobRunner.runOne(jobId);
        } finally {
            lockService.unlock(lockKey, workerId);
        }
    }
}
