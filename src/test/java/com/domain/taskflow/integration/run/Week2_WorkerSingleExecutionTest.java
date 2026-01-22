package com.domain.taskflow.integration.run;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import com.domain.taskflow.worker.JobRunner;
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
    private final JobRunner jobRunner;
    private final JobAttemptRepository attemptRepository;
    private final JobRepository jobRepository;

    @Autowired
    Week2_WorkerSingleExecutionTest(JobService jobService,
                                    JobRunner jobRunner,
                                    JobAttemptRepository attemptRepository,
                                    JobRepository jobRepository) {
        this.jobService = jobService;
        this.jobRunner = jobRunner;
        this.attemptRepository = attemptRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Runner 실행 단일성 테스트 (같은 job을 여러 스레드가 동시에 runOne() 호출 -> attempt 1개, 최종 상태 1번만 성공/실패로 확정)
     *
     * @throws Exception
     */
    @Test
    void runOne_calledConcurrently_shouldCreateOnlyOneAttempt() throws Exception {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "worker-single-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":100,\"shouldFail\":false}";
        UUID jobId = jobService.create(req).getId();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                start.await();
                jobRunner.runOne(jobId);
                return null;
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(1);
        var job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus().name()).isIn("SUCCESS", "FAILED");
    }
}
