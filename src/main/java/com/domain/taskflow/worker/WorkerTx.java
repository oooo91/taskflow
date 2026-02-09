package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobAttempt;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.metrics.JobMetrics;
import com.domain.taskflow.repo.JobAttemptRepository;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.repo.JobRepository;
import com.domain.taskflow.retry.RetryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerTx {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final RetryPolicy retryPolicy;
    private final JobMetrics jobMetrics;

    /**
     * claim + RUNNING 이벤트 + attempt 생성까지 한 번에 확정 커밋
     * - 반환값: 이번 시도의 attemptNo (claim 실패면 0)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int claimRunningAndCreateAttempt(UUID jobId, String workerId, OffsetDateTime now) {
        // claim (여기서 runningStartedAt=now가 DB에 저장/커밋됨)
        int claimed = jobRepository.claimRunning(jobId, workerId, now);
        if (claimed == 0) return 0;

        // claim 직후 상태 재조회 (attemptNo 계산을 위해)
        Job job = jobRepository.findById(jobId).orElseThrow();
        int attemptNo = job.getAttemptCount() + 1;

        // RUNNING 이벤트(outbox)
        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"RUNNING\"}"
        ));

        // attempt 기록(RUNNING)
        JobAttempt attempt = new JobAttempt(UUID.randomUUID(), jobId, attemptNo, workerId);
        jobAttemptRepository.save(attempt);

        return attemptNo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeSuccess(UUID jobId, int attemptNo) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        // attempt 업데이트
        JobAttempt attempt = jobAttemptRepository.findByJobIdAndAttemptNo(jobId, attemptNo).orElseThrow();
        attempt.markSuccess();
        jobAttemptRepository.save(attempt);

        // job 확정
        job.markSuccessFinal(); // runningStartedAt=null + attemptCount++
        jobRepository.save(job);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"SUCCESS\"}"
        ));

        jobMetrics.incSucceeded();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeFailed(UUID jobId, int attemptNo, String code, String msg) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        JobAttempt attempt = jobAttemptRepository.findByJobIdAndAttemptNo(jobId, attemptNo).orElseThrow();
        attempt.markFailed(code, msg);
        jobAttemptRepository.save(attempt);

        job.markFailedFinal(code, msg); // runningStartedAt=null + attemptCount++
        jobRepository.save(job);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"FAILED\",\"errorCode\":\"" + code + "\"}"
        ));

        jobMetrics.incFailed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeCanceled(UUID jobId, int attemptNo) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        JobAttempt attempt = jobAttemptRepository.findByJobIdAndAttemptNo(jobId, attemptNo).orElseThrow();
        attempt.markFailed("CANCELED", "cancelRequested=true");
        jobAttemptRepository.save(attempt);

        job.markCanceledFinal();
        jobRepository.save(job);

        jobEventRepository.save(new JobEvent(
                UUID.randomUUID(),
                jobId,
                "STATUS_CHANGED",
                "{\"jobId\":\"" + jobId + "\",\"to\":\"CANCELED\"}"
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailureWithRetry(UUID jobId, int attemptNo, String code, String message, OffsetDateTime now) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        JobAttempt attempt = jobAttemptRepository.findByJobIdAndAttemptNo(jobId, attemptNo).orElseThrow();
        attempt.markFailed(code, message);
        jobAttemptRepository.save(attempt);

        boolean retryable = retryPolicy.isRetryable(code);
        boolean hasMoreAttempts = attemptNo < job.getMaxAttempts(); // 1..maxAttempts 기준

        if (retryable && hasMoreAttempts) {
            OffsetDateTime nextRunAt = retryPolicy.computeNextRunAt(attemptNo, now);
            job.markRetryWait(nextRunAt, code, message); // runningStartedAt=null + attemptCount++

            jobRepository.save(job);

            jobEventRepository.save(new JobEvent(
                    UUID.randomUUID(),
                    jobId,
                    "STATUS_CHANGED",
                    "{\"jobId\":\"" + jobId + "\",\"to\":\"RETRY_WAIT\",\"nextRunAt\":\"" + nextRunAt + "\",\"errorCode\":\"" + code + "\"}"
            ));

            jobMetrics.incRetryScheduled();
        } else {
            finalizeFailed(jobId, attemptNo, code, message);
        }
    }
}
