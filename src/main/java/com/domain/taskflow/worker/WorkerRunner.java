package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobStatus;
import com.domain.taskflow.repo.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerRunner {
    private final WorkerTx workerTx;
    private final JobRepository jobRepository;
    private final Map<String, JobHandler> handlers;

    private final WorkerProperties workerProperties;

    public void runOne(UUID jobId) {
        OffsetDateTime now = OffsetDateTime.now();
        String workerId = workerProperties.getId();

        // 0) 상태 확인(가벼운 가드)
        Job before = jobRepository.findById(jobId).orElseThrow();
        if (before.getStatus() != JobStatus.PENDING) return;

        // 1) RUNNING 확정 + 이벤트 + attempt 저장을 "즉시 커밋" (reaper가 DB에서 볼 수 있게)
        int attemptNo = workerTx.claimRunningAndCreateAttempt(jobId, workerId, now);
        if (attemptNo == 0) return; // claim 실패

        // 2) handler 실행은 트랜잭션 밖에서 오래 돌려도 됨 (sleep 30초 같은 것)
        Job job = jobRepository.findById(jobId).orElseThrow();

        // 3) 취소 요청이면 즉시 취소 확정(별도 트랜잭션으로 커밋)
        if (job.isCancelRequested()) {
            workerTx.finalizeCanceled(jobId, attemptNo);
            return;
        }

        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            workerTx.finalizeFailed(jobId, attemptNo, "UNKNOWN_TYPE", "Unknown job type: " + job.getType());
            return;
        }

        try {
            handler.execute(job, attemptNo);
            workerTx.finalizeSuccess(jobId, attemptNo);
        } catch (Exception e) {
            ErrorInfo err = classifyError(e);
            workerTx.handleFailureWithRetry(jobId, attemptNo, err.code, err.message, now);
        }
    }

    private ErrorInfo classifyError(Exception e) {
        return new ErrorInfo("EXEC_ERROR", e.getMessage() == null ? e.toString() : e.getMessage());
    }

    private record ErrorInfo(String code, String message) {
    }


}
