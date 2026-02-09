package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.repo.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
    private final JobExecutionCoordinator coordinator;

    // 워커 풀
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    /**
     * 5초마다 PENDING 상태인 Job을 주워서 풀에 던진다.
     */
    @Scheduled(fixedDelay = 5000)
    public void tick() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Job> candidates = jobRepository.findRunnablePending(now, PageRequest.of(0, 10));
        for (Job job : candidates) {
            // 비동기로 실행 (워커풀)
            pool.submit(() -> coordinator.runOneWithLock(job.getId()));
        }
    }
}
