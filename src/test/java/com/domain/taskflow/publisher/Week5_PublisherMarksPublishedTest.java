package com.domain.taskflow.publisher;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.outbox.OutboxPublisher;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week5_PublisherMarksPublishedTest extends IntegrationTestBase {

    @Autowired
    JobEventRepository jobEventRepository;

    @Autowired
    JobService jobService;

    @Autowired
    OutboxPublisher outboxPublisher;

    /**
     * tick 진행 후 published_at 채워지는지 확인
     */
    @Test
    void tick_shouldMarkPublishedAt() {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "worker-single-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":100,\"shouldFail\":false}";
        UUID jobId = jobService.create(req).getId();

        // tick 실행
        outboxPublisher.tick();

        Optional<JobEvent> first = jobEventRepository.findAll().stream().findFirst();
        assertThat(first).isPresent();
        assertThat(first.get().getPublishedAt()).isNotNull();
    }
}
