package com.domain.taskflow.retry;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import com.domain.taskflow.worker.WorkerRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
public class Week4_MaxAttemptsTest extends IntegrationTestBase {

    @Autowired
    JobService jobService;

    @Autowired
    RetryScheduler retryScheduler;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobAttemptRepository attemptRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    WorkerRunner workerRunner;

    @Test
    @DisplayName("maxAttempts 초과 시 FAILED 된다 (WorkerRunner + WorkerTx)")
    void retryable_but_exceeds_maxAttempts_shouldFailFinal() throws Exception {
        // given
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk4-max-attempts-workerTx-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":10,\"failTimes\":10}"; // 계속 실패 유도

        UUID jobId = jobService.create(req).getId();

        // maxAttempts=2로 낮춤
        jdbcTemplate.update("update jobs set max_attempts = 2 where id = ?", jobId);

        // when: 1st run -> 실패 + RETRY_WAIT 스케줄링
        workerRunner.runOne(jobId);

        // handleFailureWithRetry는 REQUIRES_NEW로 커밋되므로
        // 다음 tick에서 scheduler가 RETRY_WAIT -> PENDING으로 바꾸게 해줌
        Thread.sleep(80);
        retryScheduler.tick();

        // when: 2nd run -> maxAttempts 도달 => FAILED 확정
        workerRunner.runOne(jobId);

        // then
        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(2);
    }
}
