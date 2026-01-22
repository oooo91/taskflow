package com.domain.taskflow.integration.retry;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.retry.RetryScheduler;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import com.domain.taskflow.worker.JobRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Tag("integration")
@SpringBootTest
public class Week4_MaxAttemptsTest extends IntegrationTestBase {

    @Autowired
    JobService jobService;

    @Autowired
    JobRunner jobRunner;

    @Autowired
    RetryScheduler retryScheduler;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobAttemptRepository attemptRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * maxAttempts 초과 시 FAILED 확정 테스트
     *
     * @throws Exception
     */
    @Test
    void retryable_but_exceeds_maxAttempts_shouldFailFinal() throws Exception {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk4-max-attempts-1";
        req.type = "BATCH_SIM";
        // failTimes is huge => 계속 실패
        req.payload = "{\"sleepMs\":10,\"failTimes\":10}";

        UUID jobId = jobService.create(req).getId();

        // maxAttempts=2로 낮춤
        jdbcTemplate.update("update jobs set max_attempts = 2 where id = ?", jobId);

        // 1st run -> RETRY_WAIT
        jobRunner.runOne(jobId);
        Thread.sleep(80);
        retryScheduler.tick();

        // 2nd run -> maxAttempts 도달 => FAILED 확정
        jobRunner.runOne(jobId);

        var job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(2);
    }
}
