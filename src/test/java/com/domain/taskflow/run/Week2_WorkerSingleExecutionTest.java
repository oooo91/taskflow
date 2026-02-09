package com.domain.taskflow.run;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import com.domain.taskflow.worker.WorkerRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week2_WorkerSingleExecutionTest extends IntegrationTestBase {
    private final JobService jobService;
    private final WorkerRunner workerRunner;
    private final JobAttemptRepository attemptRepository;
    private final JobRepository jobRepository;

    @Autowired
    Week2_WorkerSingleExecutionTest(
            JobService jobService,
            WorkerRunner workerRunner,
            JobAttemptRepository attemptRepository,
            JobRepository jobRepository
    ) {
        this.jobService = jobService;
        this.workerRunner = workerRunner;
        this.attemptRepository = attemptRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * WorkerRunner 단일 실행 보장 테스트
     * <p>
     * 같은 jobId에 대해 여러 스레드가 동시에 runOne()을 호출해도
     * - claimRunning CAS는 한 번만 성공
     * - attempt는 정확히 1개만 생성
     * - job 최종 상태도 한 번만 확정된다
     */
    @Test
    void runOne_calledConcurrently_shouldCreateOnlyOneAttempt() throws Exception {
        // given
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "worker-single-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":100,\"failTimes\":0}";
        UUID jobId = jobService.create(req).getId();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        // when: 동시에 runOne 호출
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                start.await();
                workerRunner.runOne(jobId);
                return null;
            });
        }

        ready.await();      // 모든 스레드 준비 완료
        start.countDown();  // 동시에 출발

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // then
        assertThat(attemptRepository.countByJobId(jobId))
                .as("동시 실행에도 attempt는 정확히 1개만 생성되어야 한다")
                .isEqualTo(1);

        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus().name())
                .as("job 상태는 단 한 번만 최종 확정된다")
                .isIn("SUCCESS", "FAILED");
    }
}
