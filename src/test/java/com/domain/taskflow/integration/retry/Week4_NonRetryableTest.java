package com.domain.taskflow.integration.retry;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import com.domain.taskflow.worker.JobRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Tag("integration")
@SpringBootTest
public class Week4_NonRetryableTest extends IntegrationTestBase {

    @Autowired
    JobService jobService;

    @Autowired
    JobRunner jobRunner;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobAttemptRepository attemptRepository;

    /**
     * UNKNOWN TYPE 일 시 즉시 FAILED
     */
    @Test
    void unknownType_shouldFailImmediately() {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk4-unknown-type-1";
        req.type = "NOT_SUPPORTED";
        req.payload = "{\"a\":1}";

        UUID jobId = jobService.create(req).getId();

        jobRunner.runOne(jobId);

        var job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getNextRunAt()).isNull();
        assertThat(attemptRepository.countByJobId(jobId)).isEqualTo(1);
    }
}
