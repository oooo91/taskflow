package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobAttempt;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.repo.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class JobRunner {
    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final JobEventRepository jobEventRepository;

    // 운영자 식별 (hostman, pod name 대신)
    private final String workerId = "workerId-1";

    // 핸들러 저장소 (type -> handler)
    private final Map<String, JobHandler> handlers;

    public JobRunner(
            JobRepository jobRepository,
            JobAttemptRepository jobAttemptRepository,
            JobEventRepository jobEventRepository,
            HttpCheckHandler httpCheckHandler,
            BatchSimHandler batchSimHandler) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.jobEventRepository = jobEventRepository;
        this.handlers = Map.of(
                "HTTP_CHECK", httpCheckHandler,
                "BATCH_SIM", batchSimHandler
        );
    }

    @Transactional
    public void runOne(UUID jobId) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. DB CAS로 RUNNING 획득
        int claimed = jobRepository.claimRunning(jobId, workerId, now);
        if (claimed == 0) {
            return; // 누가 먼저 잡았음
        }

        // 2. attempt 기록 (RUNNING)
        JobAttempt attempt = new JobAttempt(UUID.randomUUID(), jobId, 1, workerId);
        jobAttemptRepository.save(attempt);

        // 3. outbox 이벤트 기록 (RUNNING)
        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"RUNNING\"}"
        ));

        // 4. Job 조회해서 payload/type 읽기
        Job job = jobRepository.findById(jobId).orElseThrow();

        // 5. 취소 요청 체크
        if (job.isCancelRequested()) {
            // RUNNING 전에 cancelRequest=true로 들어오면 여기서 종료 처리할 수 있음
            // 현재는 단순히 FAILED 처리하거나 CANCELED 처리 정책 선택
        }

        // 6. handler 실행
        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            // 타입 미지원 → 실패 처리
            markFailed(job, attempt, "UNKNOWN_TYPE", "Unknown job type: " + job.getType());
            return;
        }

        try {
            handler.execute(job); // 여기서 실제 실행(HTTP 호출/슬립 등)

            markSuccess(job, attempt);

        } catch (Exception e) {
            markFailed(job, attempt, "EXEC_ERROR", e.getMessage());
        }
    }

    public void markSuccess(Job job, JobAttempt attempt) {
        Job managed = jobRepository.findById(job.getId()).orElseThrow();
        managed.markSuccess();

        attempt.markSuccess();
        jobAttemptRepository.save(attempt);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                job.getId(),
                "STATUS_CHANGED",
                "{\"jobId\":\"" + job.getId() + "\",\"to\":\"SUCCESS\"}"
        ));
    }

    public void markFailed(Job job, JobAttempt attempt, String code, String msg) {
        Job managed = jobRepository.findById(job.getId()).orElseThrow();
        managed.markFailed();

        attempt.markFiled(code, msg);
        jobAttemptRepository.save(attempt);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                job.getId(),
                "STATUS_CHANGED",
                "{\"jobId\":\"" + job.getId() + "\",\"to\":\"FAILED\",\"errorCode\":\"" + code + "\"}"
        ));
    }
}
