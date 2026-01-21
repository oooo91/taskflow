package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobAttempt;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.retry.RetryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobRunner {
    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final JobEventRepository jobEventRepository;
    private final RetryPolicy retryPolicy;

    // 운영자 식별 (hostman, pod name 대신)
    private final String workerId = "workerId-1";

    // Bean name → Handler
    private final Map<String, JobHandler> handlers;

    @Transactional
    public void runOne(UUID jobId) {
        OffsetDateTime now = OffsetDateTime.now();

        // 0. 실행 대상 Job 조회 attemptCount/maxAttempts 필요)
        Job jobBeforeClaim = jobRepository.findById(jobId).orElseThrow();

        // PENDING만 실행한다. (RETRY_WAIT는 scheduler가 PENDING으로 바꾼 후 실행됨)
        if (jobBeforeClaim.getStatus() != JobStatus.PENDING) {
            return;
        }

        // 1. DB CAS로 RUNNING 획득
        int claimed = jobRepository.claimRunning(jobId, workerId, now);
        if (claimed == 0) return;

        // 2. RUNNING 상태 이벤트(outbox)
        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"RUNNING\"}"
        ));

        // 3. claim 후 managed 엔티티 다시 로드
        Job job = jobRepository.findById(jobId).orElseThrow();

        // 4. attemptNo 계산 (현재까지 시도 횟수 + 1)
        int attemptNo = job.getAttemptCount() + 1;

        // 5. attempt 기록 (RUNNING)
        JobAttempt attempt = new JobAttempt(UUID.randomUUID(), jobId, attemptNo, workerId);
        jobAttemptRepository.save(attempt);

        // 6. 취소 요청 체크 (정책: 실행 시작 전에 취소 요청이면 바로 CANCELED 확정)
        if (job.isCancelRequested()) {
            job.markCanceledFinal();
            attempt.markFailed("CANCELED", "cancelRequested=true");
            jobAttemptRepository.save(attempt);

            jobEventRepository.save(new JobEvent(
                    UUID.randomUUID(),
                    jobId,
                    "STATUS_CHANGED",
                    "{\"jobId\":\"" + jobId + "\",\"to\":\"CANCELED\"}"
            ));
            return;
        }

        // 7. handler 실행
        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            // 타입 미지원 → 실패 처리
            finalizeFailed(job, attempt, "UNKNOWN_TYPE", "Unknown job type: " + job.getType());
            return;
        }

        try {
            handler.execute(job, attemptNo);
            finalizeSuccess(job, attempt);
        } catch (Exception e) {
            ErrorInfo err = classifyError(e);
            handleFailureWithRetry(job, attempt, attemptNo, err.code, err.message, now);
        }
    }

    private void finalizeSuccess(Job job, JobAttempt attempt) {
        job.markSuccessFinal();

        attempt.markSuccess();
        jobAttemptRepository.save(attempt);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                job.getId(),
                "STATUS_CHANGED",
                "{\"jobId\":\"" + job.getId() + "\",\"to\":\"SUCCESS\"}"
        ));
    }

    private void finalizeFailed(Job job, JobAttempt attempt, String code, String msg) {
        job.markFailedFinal(code, msg);

        attempt.markFailed(code, msg);
        jobAttemptRepository.save(attempt);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                job.getId(),
                "STATUS_CHANGED",
                "{\"jobId\":\"" + job.getId() + "\",\"to\":\"FAILED\",\"errorCode\":\"" + code + "\"}"
        ));
    }

    private void handleFailureWithRetry(
            Job job,
            JobAttempt attempt,
            int attemptNo,
            String code,
            String message,
            OffsetDateTime now
    ) {
        boolean retryable = retryPolicy.isRetryable(code);
        boolean hasMoreAttempts = attemptNo < job.getMaxAttempts();

        attempt.markFailed(code, message);
        jobAttemptRepository.save(attempt);

        if (retryable && hasMoreAttempts) {
            OffsetDateTime nextRunAt = retryPolicy.computeNextRunAt(attemptNo, now);
            job.markRetryWait(nextRunAt, code, message);

            JobEvent jobEvent = new JobEvent(
                    UUID.randomUUID(),
                    job.getId(),
                    "STATUS_CHANGED",
                    "{\"jobId\":\"" + job.getId() + "\",\"to\":\"RETRY_WAIT\",\"nextRunAt\":\"" + nextRunAt + "\",\"errorCode\":\"" + code + "\"}"
            );
            jobEventRepository.save(jobEvent);
        } else {
            finalizeFailed(job, attempt, code, message);
        }
    }

    private ErrorInfo classifyError(Exception e) {
        // HTTP_CHECK의 커스텀 예외면 code 분리
        if (e instanceof HttpCheckHandler.HttpStatusException he) {
            return new ErrorInfo(he.code, he.getMessage());
        }
        // 그 외는 실행 에어로 묶음 (retryable)
        return new ErrorInfo("EXEC_ERROR", e.getMessage() == null ? e.toString() : e.getMessage());
    }

    private record ErrorInfo(String code, String message) {
    }

}
