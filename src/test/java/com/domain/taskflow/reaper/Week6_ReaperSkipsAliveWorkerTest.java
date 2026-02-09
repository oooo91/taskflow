package com.domain.taskflow.reaper;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.heartbeat.HeartbeatService;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week6_ReaperSkipsAliveWorkerTest extends IntegrationTestBase {

    @Autowired
    JobService jobService;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    HeartbeatService heartbeatService;

    @Autowired
    StaleJobReaper reaper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("RUNNING 후보라도 heartbeat가 살아있으면 Reaper는 스킵한다.")
    void running_butHeartbeatAlive_shouldNotChange() {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk6-alive-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":10,\"failTimes\":0}";
        UUID jobId = jobService.create(req).getId();

        String deadWorkerId = "alive-worker-1";
        OffsetDateTime startedAt = OffsetDateTime.now().minusSeconds(60);

        jdbcTemplate.update("""
                            update jobs
                               set status = ?,
                                   worker_id = ?,
                                   running_started_at = ?,
                                   updated_at = ?
                             where id = ?
                        """,
                JobStatus.RUNNING.name(),
                deadWorkerId,
                Timestamp.from(startedAt.toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant()),
                jobId
        );

        // heartbeat 살아있게 만들기
        heartbeatService.beat("alive-worker-1", Duration.ofSeconds(30));
        assertThat(heartbeatService.isAlive("alive-worker-1")).isTrue();

        reaper.tick();

        Job updated = jobRepository.findById(jobId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(JobStatus.RUNNING);

    }
}
