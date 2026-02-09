package com.domain.taskflow.run;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week2_CasClaimRunningTest extends IntegrationTestBase {

    private final JobService jobService;
    private final JobRepository jobRepository;
    private final TransactionTemplate tx;

    @Autowired
    public Week2_CasClaimRunningTest(JobService jobService, JobRepository jobRepository, TransactionTemplate tx) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.tx = tx;
    }

    /**
     * claimRuning 동시성 테스트 (여러 스레드가 동시에 요청 -> 스레드 하나만 성공
     *
     * @throws Exception
     * @Modifiying은 트랜잭션이 필요해서 TransactionTemplate 로 감쌈
     */
    @Test
    void claimRunning_concurrently_onlyOneShouldWin() throws Exception {
        // job 하나 생성
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "cas-claim-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":50,\"shouldFail\":false}";
        var job = jobService.create(req);
        UUID jobId = job.getId();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger success = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String workerId = "worker-" + i;
            pool.submit(() -> {
                ready.countDown();
                start.await();

                Integer updated = tx.execute(status ->
                        jobRepository.claimRunning(jobId, workerId, OffsetDateTime.now())
                );

                if (updated != null && updated == 1) success.incrementAndGet();
                return null;
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(success.get()).isEqualTo(1);
    }
}
