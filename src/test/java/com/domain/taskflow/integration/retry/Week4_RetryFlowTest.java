package com.domain.taskflow.integration.retry;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobAttempt;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Tag("integration")
@SpringBootTest
public class Week4_RetryFlowTest extends IntegrationTestBase {

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

    /**
     * 재시도 성공 테스트
     *
     * @throws Exception
     */
    @Test
    void fail_then_retry_then_success() throws Exception {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk4-retry-success-1";
        req.type = "BATCH_SIM";
        // failure=2 => 첫 번째 시도 실패, 두 번째 시도 실패, 세 번째 시도 성공
        req.payload = "{\"sleepMs\":10,\"failTimes\":2}";

        UUID jobId = jobService.create(req).getId();

        // 첫 번째 실행 -> RETRY_WAIT
        jobRunner.runOne(jobId);
        Job j1 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j1.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(j1.getNextRunAt()).isNotNull();
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(1);

        // nextRunAt 까지 대기
        Thread.sleep(80);
        retryScheduler.tick();

        // 두 번째 실행 -> RETRY_WAIT
        jobRunner.runOne(jobId);
        Job j2 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j2.getStatus()).isIn(JobStatus.RETRY_WAIT, JobStatus.PENDING);

        Thread.sleep(80);
        retryScheduler.tick();

        // 세 번째 실행 -> SUCCESS
        jobRunner.runOne(jobId);
        Job j3 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j3.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(3);

        List<JobAttempt> attempts = attemptRepository.findByJobIdOrderByAttemptNoAsc(jobId);
        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).getAttemptNo()).isEqualTo(1);
        assertThat(attempts.get(1).getAttemptNo()).isEqualTo(2);
        assertThat(attempts.get(2).getAttemptNo()).isEqualTo(3);
        assertThat(attempts.get(2).getStatus().name()).isEqualTo("SUCCESS");
    }
}
