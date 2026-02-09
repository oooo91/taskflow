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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class Week6_ReaperRecoverToRetryWaitTest extends IntegrationTestBase {

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
    @DisplayName("RUNNING + heartbeat 없음 -> Reaper가 RETRY_WAIT로 회수한다.")
    void staleRunning_withoutHeartbeat_shouldMoveToRetryWait() {
        // 1. Job 생성
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "wk6-stale-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":10,\"failTimes\":0}";
        UUID jobId = jobService.create(req).getId();

        // 2. 강제로 RUNNING 상태 만들기 (워커 죽었다고 가정)
        String deadWorkerId = "dead-worker-1";
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

        // 3. heartbeat 없음 (삭제
        heartbeatService.delete(deadWorkerId);
        assertThat(heartbeatService.isAlive(deadWorkerId)).isFalse();

        // 4. reaper 실행
        reaper.tick();

        // 5. 검증
        Job updated = jobRepository.findById(jobId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(updated.getNextRunAt()).isNotNull();
    }
}
