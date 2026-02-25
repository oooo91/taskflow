package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.repo.JobRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class JobWorker {

    private final JobRepository jobRepository;
    private final JobExecutionCoordinator coordinator;
    private final WorkerProperties workerProperties;
    private final ExecutorService pool; // 워커풀

    // 같은 jobId를 중복 submit 방지 (단일 JVM에서 큐 폭증 방지)
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public JobWorker(JobRepository jobRepository,
                     JobExecutionCoordinator coordinator,
                     WorkerProperties workerProperties) {
        this.jobRepository = jobRepository;
        this.coordinator = coordinator;
        this.workerProperties = workerProperties;

        int threads = workerProperties.getPoolSize();
        log.info("스레드 수: {}", threads);

        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setName("taskflow-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * N초마다 PENDING 상태인 Job을 주워서 풀에 던진다.
     */
    @Scheduled(fixedDelayString = "${taskflow.worker.poll-ms:5000}")
    public void tick() {
        OffsetDateTime now = OffsetDateTime.now();
        int fetchSize = workerProperties.getFetchSize();

        List<Job> candidates = jobRepository.findRunnablePending(now, PageRequest.of(0, fetchSize));
        for (Job job : candidates) {
            UUID jobId = job.getId();
            if (!inFlight.add(jobId)) continue;

            // 비동기로 실행 (워커풀)
            pool.submit(() -> {
                try {
                    coordinator.runOneWithLock(jobId);
                } finally {
                    inFlight.remove(jobId);
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
    }
}
