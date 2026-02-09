package com.domain.taskflow.run;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week2_IdempotentCreateConcurrencyTest extends IntegrationTestBase {
    private final JobService jobService;

    @Autowired
    Week2_IdempotentCreateConcurrencyTest(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 동시 멱등 생성 테스트 (한 번에 같은 job 여러 번 생성 시 -> job은 1개만 생성)
     *
     * @throws Exception
     */
    @Test
    void sameJobKey_concurrentCreate_shouldReturnSameJob() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        ConcurrentLinkedQueue<String> ids = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                start.await();

                JobCreateRequest req = new JobCreateRequest();
                req.jobKey = "idem-concurrent-1";
                req.type = "BATCH_SIM";
                req.payload = "{\"sleepMs\":50,\"shouldFail\":false}";

                var job = jobService.create(req);
                ids.add(job.getId().toString());
                return null;
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        Set<String> unique = Set.copyOf(ids);
        assertThat(unique).hasSize(1); // 핵심: job id 1개만
    }
}
