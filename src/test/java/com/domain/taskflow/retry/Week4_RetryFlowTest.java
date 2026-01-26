package com.domain.taskflow.retry;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobAttempt;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
public class Week4_RetryFlowTest extends IntegrationTestBase {

    @Autowired
    JobService jobService;

    @Autowired
    WorkerRunner workerRunner;

    @Autowired
    RetryScheduler retryScheduler;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobAttemptRepository attemptRepository;

    @Test
    @DisplayName("재시도가 성공한다. (WorkerRunner + WorkerTx)")
    void fail_then_retry_then_success() throws Exception {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk4-retry-success-1";
        req.type = "BATCH_SIM";
        // failTimes=2 => 1회 실패, 2회 실패, 3회 성공
        req.payload = "{\"sleepMs\":10,\"failTimes\":2}";

        UUID jobId = jobService.create(req).getId();

        // 1st run -> RETRY_WAIT
        workerRunner.runOne(jobId);
        Job j1 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j1.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(j1.getNextRunAt()).isNotNull();
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(1);

        // nextRunAt 까지 대기 + scheduler로 PENDING 회수
        Thread.sleep(80);
        retryScheduler.tick();

        // 2nd run -> RETRY_WAIT (또 실패)
        workerRunner.runOne(jobId);
        Job j2 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j2.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(j2.getNextRunAt()).isNotNull();
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(2);

        Thread.sleep(80);
        retryScheduler.tick();

        // 3rd run -> SUCCESS
        workerRunner.runOne(jobId);
        Job j3 = jobRepository.findById(jobId).orElseThrow();
        assertThat(j3.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(3);

        // attemptNo 연속 증가 검증
        List<JobAttempt> attempts = attemptRepository.findByJobIdOrderByAttemptNoAsc(jobId);
        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).getAttemptNo()).isEqualTo(1);
        assertThat(attempts.get(1).getAttemptNo()).isEqualTo(2);
        assertThat(attempts.get(2).getAttemptNo()).isEqualTo(3);
        assertThat(attempts.get(2).getStatus().name()).isEqualTo("SUCCESS");
    }
}
