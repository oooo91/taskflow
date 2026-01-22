package com.domain.taskflow.publisher;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.service.JobService;
import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Tag("integration")
@AutoConfigureMockMvc
public class Week5_SseReplayTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JobService jobService;

    @Autowired
    JobEventRepository jobEventRepository;

    @Test
    void stream_shouldReplayEventsAfterLastEventId() throws Exception {
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = "worker-single-1";
        req.type = "BATCH_SIM";
        req.payload = "{\"sleepMs\":100,\"shouldFail\":false}";
        UUID jobId = jobService.create(req).getId();

        // seq 얻기 위해 이벤트 하나가 필요하다.
        List<JobEvent> all = jobEventRepository.findAll();
        assertThat(all).isNotEmpty();

        // 가장 최신 seq보다 작은 값을 Last-event-id로 주면 백로그가 나와야 한다.
        long minSeq = all.stream().mapToLong(e -> e.getSeq()).min().orElse(1);
        long lastSeq = Math.max(0, minSeq - 1);

        var res = mockMvc.perform(get("/stream/jobs")
                        .header("Last-Event-ID", String.valueOf(lastSeq)))
                .andExpect(status().isOk())
                .andReturn();
    }
}
