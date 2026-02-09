package com.domain.taskflow.unit.service;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.service.JobService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@Tag("unit")
@ExtendWith(MockitoExtension.class)
class Week1_JobServiceTest {

    @Mock
    JobRepository jobRepository;

    @Mock
    JobEventRepository jobEventRepository;

    @InjectMocks
    JobService jobService;

    @Test
    void create_withJobKey_whenExisting_andDoesNotWriteEvent() {
        // given
        String jobKey = "dup-key";
        Job existing = new Job(UUID.randomUUID(), jobKey, "HTTP_CHECK", "{\"x\":1}", null);

        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = jobKey;
        req.type = "HTTP_CHECK";
        req.payload = "{\"x\":1}";
        req.scheduledAt = null;

        when(jobRepository.findByJobKey(jobKey)).thenReturn(Optional.of(existing));

        // when
        Job result = jobService.create(req);

        // then
        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(jobRepository, never()).save(any(Job.class));
        verify(jobEventRepository, never()).save(any(JobEvent.class));
    }

    @Test
    void create_withoutJobKey_savesJob_andWritesJobCreatedEvent() {
        // given
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = null;
        req.type = "HTTP_CHECK";
        req.payload = "{\"url\":\"https://example.com\"}";
        req.scheduledAt = OffsetDateTime.now().plusMinutes(1);

        // save(Job) 가 호출되면 그 job 그대로 반환되게
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Job saved = jobService.create(req);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getType()).isEqualTo("HTTP_CHECK");

        // 이벤트가 같은 트랜잭션에서 저장되는지 확인
        // jobEventRepository.save(...)가 이미 호출됐는지 확인 -> 호출됐다면, 그때 save에 전달된 인자를 가져와 evCaptor에 저장
        ArgumentCaptor<JobEvent> evCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(evCaptor.capture());

        JobEvent ev = evCaptor.getValue();
        assertThat(ev).isNotNull();
        assertThat(ev.getJobId()).isEqualTo(saved.getId());
        assertThat(ev.getEventType()).isEqualTo("JOB_CREATED");
        assertThat(ev.getPayload()).contains(saved.getId().toString());
    }

    @Test
    void create_withJobKey_whenSaveThrowsUniqueViolation_returnsExistingFromRepository() {
        // given
        String jobKey = "same-key";
        JobCreateRequest req = new JobCreateRequest();
        req.jobKey = jobKey;
        req.type = "HTTP_CHECK";
        req.payload = "{\"x\":1}";

        // catch 블록에서 다시 findByJobKey를 호출하면 existing 반환
        Job existing = new Job(UUID.randomUUID(), jobKey, "HTTP_CHECK", "{\"x\":1}", null);

        // 1번째 호출은 empty, 2번째 호출은 existing
        when(jobRepository.findByJobKey(jobKey))
                .thenReturn(Optional.empty(), Optional.of(existing));

        // save에서 unique 위반이 났다고 가정
        when(jobRepository.save(any(Job.class)))
                .thenThrow(new DataIntegrityViolationException("중복된 엔티티입니다."));

        // when
        Job result = jobService.create(req);

        // then
        assertThat(result.getId()).isEqualTo(existing.getId());
        // unique 위반 케이스에서는 이벤트를 새로 쓰지 않게 되어 있음(기존 job 리턴)
        verify(jobEventRepository, never()).save(any());
    }

    @Test
    void get_whenNotFound_throwsEntityNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> jobService.get(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void cancel_whenPending_setsCanceled_andWritesEvent() {
        // given
        UUID id = UUID.randomUUID();
        Job job = new Job(id, null, "HTTP_CHECK", "{\"x\":1}", null);
        // 생성 시 PENDING

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Job result = jobService.cancel(id);

        // then
        assertThat(result.getStatus()).isEqualTo(JobStatus.CANCELED);
        assertThat(result.isCancelRequested()).isFalse();

        ArgumentCaptor<JobEvent> evCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(evCaptor.capture());
        JobEvent ev = evCaptor.getValue();
        assertThat(ev.getEventType()).isEqualTo("JOB_CANCEL_REQUESTED");
        assertThat(ev.getPayload()).contains("\"status\":\"" + result.getStatus() + "\"");
    }

    @Test
    void cancel_whenRunning_setsCancelRequestedTrue_andWritesEvent() {
        // given
        UUID id = UUID.randomUUID();
        Job job = new Job(id, null, "HTTP_CHECK", "{\"x\":1}", null);

        // 강제로 RUNNING 상태로 만들기: (테스트 편의상 reflection 사용)
        setStatus(job, JobStatus.RUNNING);

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Job result = jobService.cancel(id);

        // then
        assertThat(result.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(result.isCancelRequested()).isTrue();

        verify(jobEventRepository).save(any(JobEvent.class));
    }

    // 테스트 유틸: 엔티티에 setter가 없으므로 리플렉션으로 상태 주입
    private static void setStatus(Job job, JobStatus status) {
        try {
            var f = Job.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(job, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}